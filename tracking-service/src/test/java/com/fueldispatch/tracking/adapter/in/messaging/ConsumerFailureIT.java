package com.fueldispatch.tracking.adapter.in.messaging;

import static com.fueldispatch.tracking.adapter.in.messaging.OrderEventListenerTest.example;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fueldispatch.tracking.TestcontainersConfiguration;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.OrderTracking;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import reactor.core.publisher.Mono;

/**
 * TRK-1.4 under failure: an offset is never committed for an event that was not saved, and one
 * invalid payload does not block its partition.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ConsumerFailureIT.FailureConfig.class})
class ConsumerFailureIT {

    private static final String TOPIC = "dispatch.orders.v1";
    private static final String GROUP = "tracking-service";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /**
     * One partition keeps the offsets easy to read. Short MongoDB timeouts make each attempt fail
     * within a second while the container is paused, so Spring Kafka's default handler (10 attempts
     * without pause) would give up well inside the outage below.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailureConfig {

        @Bean
        NewTopic dispatchOrdersTopic() {
            return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        MongoClientSettingsBuilderCustomizer fastFailingMongo() {
            return builder ->
                    builder.applyToClusterSettings(
                                    cluster ->
                                            cluster.serverSelectionTimeout(
                                                    500, TimeUnit.MILLISECONDS))
                            .applyToSocketSettings(
                                    socket ->
                                            socket.connectTimeout(500, TimeUnit.MILLISECONDS)
                                                    .readTimeout(500, TimeUnit.MILLISECONDS));
        }
    }

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private TrackingRepository repository;
    @Autowired private KafkaAdmin kafkaAdmin;
    @Autowired private MongoDBContainer mongo;

    @Test
    void mongoOutage_offsetStaysUncommittedAndEventIsSavedWhenMongoReturns() throws Exception {
        // Warm up: the consumer is assigned and has processed something.
        String warmUp = UUID.randomUUID().toString();
        send(warmUp, created(warmUp));
        await().atMost(TIMEOUT).until(() -> tracking(warmUp).isPresent());

        String orderId = UUID.randomUUID().toString();
        pauseMongo();
        try {
            send(orderId, created(orderId));
            // Longer than Spring Kafka's default 10 fast attempts take with these timeouts.
            Thread.sleep(12_000);

            try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                assertThat(committedOffset(admin)).isLessThan(endOffset(admin));
            }
        } finally {
            unpauseMongo();
        }

        await().atMost(TIMEOUT).until(() -> tracking(orderId).isPresent());
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(TIMEOUT)
                    .untilAsserted(
                            () -> assertThat(committedOffset(admin)).isEqualTo(endOffset(admin)));
        }
    }

    @Test
    void invalidPayload_isSkippedAndNextEventOfItsPartitionIsApplied() throws Exception {
        String orderId = UUID.randomUUID().toString();

        send(orderId, "{not json");
        send(orderId, created(orderId));

        await().atMost(Duration.ofSeconds(20)).until(() -> tracking(orderId).isPresent());
    }

    private void pauseMongo() {
        DockerClientFactory.lazyClient().pauseContainerCmd(mongo.getContainerId()).exec();
    }

    private void unpauseMongo() {
        DockerClientFactory.lazyClient().unpauseContainerCmd(mongo.getContainerId()).exec();
    }

    private void send(String orderId, String payload) {
        kafkaTemplate.send(TOPIC, orderId, payload).join();
    }

    private static String created(String orderId) throws Exception {
        return example("order-created.json")
                .replaceAll("\"orderId\": \"[^\"]+\"", "\"orderId\": \"" + orderId + "\"")
                .replaceAll(
                        "\"eventId\": \"[^\"]+\"", "\"eventId\": \"" + UUID.randomUUID() + "\"");
    }

    private Optional<OrderTracking> tracking(String orderId) {
        return repository
                .findById(UUID.fromString(orderId))
                .map(VersionedTracking::tracking)
                .onErrorResume(e -> Mono.empty())
                .blockOptional();
    }

    private static long committedOffset(AdminClient admin) throws Exception {
        OffsetAndMetadata committed =
                admin.listConsumerGroupOffsets(GROUP)
                        .partitionsToOffsetAndMetadata()
                        .get()
                        .get(PARTITION);
        return committed == null ? 0 : committed.offset();
    }

    private static long endOffset(AdminClient admin) throws Exception {
        return admin.listOffsets(Map.of(PARTITION, OffsetSpec.latest()))
                .partitionResult(PARTITION)
                .get()
                .offset();
    }
}
