package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.application.port.in.ChangeOrderStatusUseCase;
import com.fueldispatch.dispatch.application.port.in.ChangeStatusCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.out.DomainEventPublisher;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox shares the order's transaction (EVT-1.1, EVT-1.2) on a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxTransactionIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private CreateOrderUseCase createOrder;
    @Autowired private ChangeOrderStatusUseCase changeOrderStatus;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DomainEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    // EVT-1.1
    @Test
    void useCases_commit_storeOneUnpublishedOutboxRowPerEvent() throws Exception {
        DispatchOrder order = createOrder.create(command());
        changeOrderStatus.changeStatus(
                new ChangeStatusCommand(order.id(), OrderAction.APPROVE, null));

        List<Map<String, Object>> rows = outboxRowsOf(order.id());

        assertThat(rows)
                .extracting(row -> row.get("event_type"))
                .containsExactly("OrderCreated", "OrderApproved");
        for (Map<String, Object> row : rows) {
            JsonNode payload = JSON.readTree((String) row.get("payload"));
            assertThat(row.get("id")).isEqualTo(UUID.fromString(payload.get("eventId").asText()));
            assertThat(row.get("aggregate_id")).isEqualTo(order.id().value());
            assertThat(((Timestamp) row.get("occurred_at")).toInstant())
                    .isEqualTo(Instant.parse(payload.get("occurredAt").asText()));
            assertThat(row.get("published_at")).isNull();
            assertThat(EventContract.v1().violations(payload)).isEmpty();
        }
    }

    // EVT-1.2
    @Test
    void transaction_rolledBackAfterPublishing_storesNeitherOrderNorOutboxRows() {
        DispatchOrder order = newOrder();
        OrderId id = order.id();

        assertThatThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        status -> {
                                            orderRepository.save(order);
                                            eventPublisher.publish(order.pullDomainEvents());
                                            throw new IllegalStateException("boom");
                                        }))
                .hasMessage("boom");

        assertThat(outboxRowsOf(id)).isEmpty();
        assertThat(orderRepository.findById(id)).isEmpty();
    }

    // EVT-1.1: the outbox never writes on its own, only inside the caller's transaction.
    @Test
    void publish_withoutTransaction_isRejectedAndStoresNothing() {
        DispatchOrder order = newOrder();

        assertThatThrownBy(() -> eventPublisher.publish(order.pullDomainEvents()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxRowsOf(order.id())).isEmpty();
    }

    private List<Map<String, Object>> outboxRowsOf(OrderId id) {
        return jdbc.queryForList(
                "select id, aggregate_id, event_type, payload::text as payload, occurred_at,"
                        + " published_at from outbox_event where aggregate_id = ?"
                        + " order by occurred_at",
                id.value());
    }

    private DispatchOrder newOrder() {
        CreateOrderCommand c = command();
        return DispatchOrder.create(
                c.vessel(), c.berth(), c.fuelType(), c.quantity(), c.deliveryWindow(), clock);
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
