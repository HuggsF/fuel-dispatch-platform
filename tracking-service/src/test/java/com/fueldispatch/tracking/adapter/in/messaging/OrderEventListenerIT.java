package com.fueldispatch.tracking.adapter.in.messaging;

import static com.fueldispatch.tracking.adapter.in.messaging.OrderEventListenerTest.example;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fueldispatch.tracking.TestcontainersConfiguration;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.HistoryEntry;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * TRK-1.1, TRK-1.2, TRK-1.4 end to end: the contract examples, published the way dispatch-service
 * publishes them (key = orderId, JSON as a string), end up as tracking documents.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, OrderEventListenerIT.TopicConfig.class})
class OrderEventListenerIT {

    private static final String TOPIC = "dispatch.orders.v1";
    private static final String GROUP = "tracking-service";
    private static final String EXAMPLE_ORDER = "1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10";
    private static final String CANCELLED_ORDER = "6a3d9f82-1c5e-4b7a-8e24-9f6b3c1d7a20";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** The topic is owned by dispatch-service; the test creates it as it would exist there. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfig {

        @Bean
        NewTopic dispatchOrdersTopic() {
            return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private TrackingRepository repository;
    @Autowired private KafkaAdmin kafkaAdmin;

    private void send(String orderId, String payload) {
        kafkaTemplate.send(TOPIC, orderId, payload).join();
    }

    private Optional<OrderTracking> tracking(String orderId) {
        return repository
                .findById(UUID.fromString(orderId))
                .map(VersionedTracking::tracking)
                .blockOptional();
    }

    /** An example rewritten to a fresh order, so tests do not see each other's documents. */
    private static String forOrder(String exampleJson, String orderId, UUID eventId) {
        return exampleJson
                .replaceAll("\"orderId\": \"[^\"]+\"", "\"orderId\": \"" + orderId + "\"")
                .replaceAll("\"eventId\": \"[^\"]+\"", "\"eventId\": \"" + eventId + "\"");
    }

    // TRK-1.1, TRK-4.2
    @Test
    void contractExamples_produceTrackingDocuments() throws Exception {
        send(EXAMPLE_ORDER, example("order-created.json"));
        send(EXAMPLE_ORDER, example("order-approved.json"));
        send(EXAMPLE_ORDER, example("order-dispatched.json"));
        send(EXAMPLE_ORDER, example("order-delivered.json"));
        send(CANCELLED_ORDER, example("order-cancelled.json"));

        await().atMost(TIMEOUT)
                .ignoreException(NoSuchElementException.class)
                .untilAsserted(
                        () -> {
                            OrderTracking delivered = tracking(EXAMPLE_ORDER).orElseThrow();
                            assertThat(delivered.currentStatus())
                                    .isEqualTo(TrackingStatus.DELIVERED);
                            assertThat(delivered.history())
                                    .extracting(HistoryEntry::status)
                                    .containsExactly(
                                            TrackingStatus.CREATED,
                                            TrackingStatus.APPROVED,
                                            TrackingStatus.DISPATCHED,
                                            TrackingStatus.DELIVERED);
                            assertThat(delivered.summary().vesselName())
                                    .isEqualTo("MV Atlantic Star");
                        });
        await().atMost(TIMEOUT)
                .ignoreException(NoSuchElementException.class)
                .untilAsserted(
                        () -> {
                            OrderTracking cancelled = tracking(CANCELLED_ORDER).orElseThrow();
                            assertThat(cancelled.currentStatus())
                                    .isEqualTo(TrackingStatus.CANCELLED);
                            assertThat(cancelled.history().getFirst().reason())
                                    .isEqualTo(
                                            "Vessel departed the berth before the delivery"
                                                    + " window.");
                        });
    }

    // TRK-1.2
    @Test
    void duplicateEvent_isIgnored() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String created = forOrder(example("order-created.json"), orderId, UUID.randomUUID());
        String approved = forOrder(example("order-approved.json"), orderId, UUID.randomUUID());

        send(orderId, created);
        send(orderId, created);
        // Same key, same partition: once APPROVED is visible, the duplicate was consumed too.
        send(orderId, approved);

        await().atMost(TIMEOUT)
                .ignoreException(NoSuchElementException.class)
                .untilAsserted(
                        () -> {
                            OrderTracking tracking = tracking(orderId).orElseThrow();
                            assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.APPROVED);
                            assertThat(tracking.history()).hasSize(2);
                        });
    }

    // TRK-1.4
    @Test
    void consumerGroup_commitsOffsetsOfProcessedEvents() throws Exception {
        String orderId = UUID.randomUUID().toString();
        send(orderId, forOrder(example("order-created.json"), orderId, UUID.randomUUID()));
        await().atMost(TIMEOUT).until(() -> tracking(orderId).isPresent());

        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(TIMEOUT)
                    .ignoreException(NoSuchElementException.class)
                    .untilAsserted(
                            () -> assertThat(committedOffsets(admin)).isEqualTo(endOffsets(admin)));
        }
    }

    private static Map<TopicPartition, Long> committedOffsets(AdminClient admin) throws Exception {
        return admin
                .listConsumerGroupOffsets(GROUP)
                .partitionsToOffsetAndMetadata()
                .get()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().topic().equals(TOPIC))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> offsetOf(e.getValue())));
    }

    private static long offsetOf(OffsetAndMetadata metadata) {
        return metadata == null ? 0 : metadata.offset();
    }

    /**
     * End offsets of the partitions that have messages; empty partitions have nothing to commit.
     */
    private static Map<TopicPartition, Long> endOffsets(AdminClient admin) throws Exception {
        Map<TopicPartition, OffsetSpec> latest =
                admin
                        .describeTopics(List.of(TOPIC))
                        .allTopicNames()
                        .get()
                        .get(TOPIC)
                        .partitions()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        p -> new TopicPartition(TOPIC, p.partition()),
                                        p -> OffsetSpec.latest()));
        return admin.listOffsets(latest).all().get().entrySet().stream()
                .filter(e -> e.getValue().offset() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }
}
