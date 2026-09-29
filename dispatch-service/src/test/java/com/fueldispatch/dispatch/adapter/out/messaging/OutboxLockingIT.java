package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Several relays at once never publish a row twice (EVT-2.5) and never reorder the events of one
 * order (EVT-2.6), on real PostgreSQL and Kafka. The scheduler is slowed to once an hour so the
 * test drives the relays itself.
 */
@SpringBootTest(properties = "outbox.relay.interval=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxLockingIT {

    private static final Instant T0 = Instant.parse("2026-10-05T14:00:00Z");
    private static final String[] LIFECYCLE = {"OrderCreated", "OrderApproved", "OrderDispatched"};

    @Autowired private SpringDataOutboxRepository repository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private OutboxRelay relay;
    @Autowired private KafkaConnectionDetails kafka;

    @BeforeEach
    void emptyOutbox() {
        repository.deleteAll();
    }

    // EVT-2.5, EVT-2.6
    @Test
    void lockNextBatch_whileAnotherRelayHoldsItsBatch_getsNeitherItsRowsNorTheirSuccessors()
            throws Exception {
        UUID orderX = UUID.randomUUID();
        UUID orderY = UUID.randomUUID();
        OutboxEventJpaEntity x1 = save(orderX, "OrderCreated", T0);
        OutboxEventJpaEntity x2 = save(orderX, "OrderApproved", T0.plusSeconds(1));
        OutboxEventJpaEntity y1 = save(orderY, "OrderCreated", T0.plusSeconds(2));

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<List<UUID>> relayA =
                CompletableFuture.supplyAsync(
                        () ->
                                transactionTemplate.execute(
                                        status -> {
                                            List<UUID> batch = ids(repository.lockNextBatch());
                                            locked.countDown();
                                            awaitLatch(release);
                                            return batch;
                                        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        List<UUID> relayB = transactionTemplate.execute(status -> ids(repository.lockNextBatch()));
        release.countDown();

        // A takes the head of each order; x2 waits behind x1 even though it is not locked.
        assertThat(relayA.get(10, TimeUnit.SECONDS)).containsExactly(x1.getId(), y1.getId());
        assertThat(relayB).isEmpty();

        transactionTemplate.executeWithoutResult(
                status ->
                        repository.findById(x1.getId()).orElseThrow().markPublished(Instant.now()));
        List<UUID> next = transactionTemplate.execute(status -> ids(repository.lockNextBatch()));
        assertThat(next).containsExactly(x2.getId(), y1.getId());
    }

    // EVT-2.5, EVT-2.6
    @Test
    void twoRelaysInParallel_publishEveryEventOnceAndInOrderPerOrder() throws Exception {
        List<UUID> orders = new ArrayList<>();
        Instant at = T0;
        for (int order = 0; order < 40; order++) {
            orders.add(UUID.randomUUID());
        }
        // Interleave orders so each relay batch mixes them.
        for (String eventType : LIFECYCLE) {
            for (UUID order : orders) {
                save(order, eventType, at);
                at = at.plusMillis(1);
            }
        }
        int expected = orders.size() * LIFECYCLE.length;

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(DispatchOrderEventMapper.TOPIC));

            CompletableFuture<Void> first = CompletableFuture.runAsync(this::relayUntilEmpty);
            CompletableFuture<Void> second = CompletableFuture.runAsync(this::relayUntilEmpty);
            CompletableFuture.allOf(first, second).get(60, TimeUnit.SECONDS);

            Map<String, List<String>> typesByOrder = new HashMap<>();
            Map<String, Integer> timesSeen = new HashMap<>();
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .until(
                            () -> {
                                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                                return records.size() >= expected;
                            });
            // Drain a little longer so a duplicate would show up.
            consumer.poll(Duration.ofSeconds(1)).forEach(records::add);

            for (ConsumerRecord<String, String> record : records) {
                timesSeen.merge(header(record, "eventId"), 1, Integer::sum);
                typesByOrder
                        .computeIfAbsent(record.key(), k -> new ArrayList<>())
                        .add(header(record, "eventType"));
            }
            assertThat(timesSeen).hasSize(expected).allSatisfy((id, n) -> assertThat(n).isOne());
            assertThat(typesByOrder).hasSize(orders.size());
            assertThat(typesByOrder.values())
                    .allSatisfy(t -> assertThat(t).containsExactly(LIFECYCLE));
        }
    }

    /** One relay instance: keeps polling until nothing is left to publish. */
    private void relayUntilEmpty() {
        while (transactionTemplate.execute(status -> repository.countByPublishedAtIsNull() > 0)) {
            relay.relayPending();
        }
    }

    private OutboxEventJpaEntity save(UUID orderId, String eventType, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        return repository.save(
                new OutboxEventJpaEntity(
                        eventId,
                        orderId,
                        eventType,
                        "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\"}",
                        occurredAt));
    }

    private static List<UUID> ids(List<OutboxEventJpaEntity> rows) {
        return rows.stream().map(OutboxEventJpaEntity::getId).toList();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        String.join(",", kafka.getConsumerBootstrapServers()),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "outbox-locking-it-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
