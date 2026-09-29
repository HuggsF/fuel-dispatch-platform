package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.application.port.in.ChangeOrderStatusUseCase;
import com.fueldispatch.dispatch.application.port.in.ChangeStatusCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The scheduled relay takes outbox rows to Kafka and marks them published, on real PostgreSQL and
 * Kafka (EVT-2.1, EVT-2.2, EVT-2.3, EVT-3.1, EVT-3.3).
 */
@SpringBootTest(properties = "outbox.relay.interval=100")
@Import(TestcontainersConfiguration.class)
class OutboxRelayIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private CreateOrderUseCase createOrder;
    @Autowired private ChangeOrderStatusUseCase changeOrderStatus;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaConnectionDetails kafka;

    @Test
    void committedEvents_reachTheTopicInOrderKeyedByOrderIdAndAreMarkedPublished()
            throws Exception {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(DispatchOrderEventMapper.TOPIC));

            DispatchOrder order = createOrder.create(command());
            changeOrderStatus.changeStatus(
                    new ChangeStatusCommand(order.id(), OrderAction.APPROVE, null));
            String orderId = order.id().value().toString();

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .until(
                            () -> {
                                consumer.poll(Duration.ofMillis(200))
                                        .forEach(
                                                r -> {
                                                    if (orderId.equals(r.key())) {
                                                        records.add(r);
                                                    }
                                                });
                                return records.size() >= 2;
                            });

            assertThat(records)
                    .extracting(r -> header(r, "eventType"))
                    .containsExactly("OrderCreated", "OrderApproved");
            assertThat(records)
                    .extracting(ConsumerRecord::partition)
                    .containsOnly(records.getFirst().partition());
            for (ConsumerRecord<String, String> record : records) {
                JsonNode payload = JSON.readTree(record.value());
                // The wire value is the mapper's JSON as written, not re-serialized by the DB.
                assertThat(record.value()).startsWith("{\"eventId\":").doesNotContain("\": ");
                assertThat(EventContract.v1().violations(payload)).isEmpty();
                assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
                assertThat(header(record, "eventId")).isEqualTo(payload.get("eventId").asText());
            }

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () ->
                                    assertThat(publishedAtOf(order.id().value()))
                                            .hasSize(2)
                                            .doesNotContainNull());
        }
    }

    private List<Instant> publishedAtOf(UUID orderId) {
        return jdbc
                .queryForList(
                        "select published_at from outbox_event where aggregate_id = ?",
                        Timestamp.class,
                        orderId)
                .stream()
                .map(t -> t == null ? null : t.toInstant())
                .toList();
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        String.join(",", kafka.getConsumerBootstrapServers()),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "outbox-relay-it-" + UUID.randomUUID(),
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

    private static CreateOrderCommand command() {
        return new CreateOrderCommand(
                new Vessel("MV Atlantic Star", "9321483"),
                new Berth("B-03"),
                FuelType.VLSFO,
                new Quantity(new BigDecimal("850.5")),
                new DeliveryWindow(
                        Instant.parse("2026-10-06T08:00:00Z"),
                        Instant.parse("2026-10-06T14:00:00Z")));
    }
}
