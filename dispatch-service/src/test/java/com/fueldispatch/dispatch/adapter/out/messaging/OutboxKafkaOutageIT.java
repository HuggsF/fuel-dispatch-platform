package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka goes down while the service runs (EVT-1.3, EVT-2.4): commands are still accepted, the event
 * waits in the outbox, and once Kafka is back it is published — without one extra copy per failed
 * poll. The broker is paused, not stopped, so the producer keeps its connection and metadata, as in
 * a real outage of a running system.
 */
@SpringBootTest(properties = "outbox.relay.enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxKafkaOutageIT {

    private static final int FAILED_POLLS = 3;

    @Autowired private CreateOrderUseCase createOrder;
    @Autowired private SpringDataOutboxRepository repository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ProducerFactory<?, ?> producerFactory;
    @Autowired private Clock clock;
    @Autowired private KafkaContainer kafkaContainer;
    @Autowired private KafkaConnectionDetails kafka;

    @BeforeEach
    void emptyOutbox() {
        repository.deleteAll();
    }

    /**
     * A send the relay gave up on must be given up by the producer too; otherwise the producer
     * keeps it buffered and delivers it later, next to the relay's retry.
     */
    @Test
    void producer_givesUpOnASendBeforeTheRelayStopsWaitingForIt() {
        Object deliveryTimeout =
                producerFactory
                        .getConfigurationProperties()
                        .get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);

        assertThat(deliveryTimeout).as("delivery.timeout.ms").isNotNull();
        assertThat(Duration.ofMillis(Long.parseLong(String.valueOf(deliveryTimeout))))
                .isLessThan(OutboxRelay.SEND_TIMEOUT);
    }

    @Test
    void kafkaDown_commandsStillWork_andTheEventIsPublishedOnceKafkaIsBack() throws Exception {
        OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate, clock);
        // A running service already has its connection and topic metadata.
        kafkaTemplate
                .send(DispatchOrderEventMapper.TOPIC, "warm-up", "{}")
                .get(30, TimeUnit.SECONDS);

        DispatchOrder order;
        pauseKafka();
        try {
            long start = System.nanoTime();
            order = createOrder.create(command());
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .as("a command never waits for Kafka")
                    .isLessThan(Duration.ofSeconds(2));

            for (int poll = 0; poll < FAILED_POLLS; poll++) {
                transactionTemplate.executeWithoutResult(status -> relay.relayPending());
            }
            assertThat(rowOf(order).getPublishedAt()).as("still waiting in the outbox").isNull();
        } finally {
            unpauseKafka();
        }

        await().atMost(Duration.ofSeconds(60))
                .until(
                        () -> {
                            transactionTemplate.executeWithoutResult(
                                    status -> relay.relayPending());
                            return rowOf(order).getPublishedAt() != null;
                        });

        // A request already on the wire when the broker froze may still be appended when it
        // resumes (at-least-once), but the failed polls must not each add a copy.
        assertThat(copiesOnTheTopic(rowOf(order).getId())).isBetween(1, 2);
    }

    private int copiesOnTheTopic(UUID eventId) {
        AtomicInteger copies = new AtomicInteger();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(DispatchOrderEventMapper.TOPIC));
            long idleUntil = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < idleUntil) {
                consumer.poll(Duration.ofMillis(500))
                        .forEach(
                                record -> {
                                    var header = record.headers().lastHeader("eventId");
                                    if (header != null
                                            && eventId.toString()
                                                    .equals(
                                                            new String(
                                                                    header.value(),
                                                                    StandardCharsets.UTF_8))) {
                                        copies.incrementAndGet();
                                    }
                                });
            }
        }
        return copies.get();
    }

    private OutboxEventJpaEntity rowOf(DispatchOrder order) {
        return repository.findAll().stream()
                .filter(row -> row.getAggregateId().equals(order.id().value()))
                .findFirst()
                .orElseThrow();
    }

    private void pauseKafka() {
        kafkaContainer.getDockerClient().pauseContainerCmd(kafkaContainer.getContainerId()).exec();
    }

    private void unpauseKafka() {
        kafkaContainer
                .getDockerClient()
                .unpauseContainerCmd(kafkaContainer.getContainerId())
                .exec();
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        String.join(",", kafka.getConsumerBootstrapServers()),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "outbox-outage-it-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        StringDeserializer.class));
    }

    private static CreateOrderCommand command() {
        return new CreateOrderCommand(
                new Vessel("MV Kafka Down", "9321484"),
                new Berth("B-07"),
                FuelType.MGO,
                new Quantity(new BigDecimal("120")),
                new DeliveryWindow(
                        Instant.parse("2026-10-07T08:00:00Z"),
                        Instant.parse("2026-10-07T12:00:00Z")));
    }
}
