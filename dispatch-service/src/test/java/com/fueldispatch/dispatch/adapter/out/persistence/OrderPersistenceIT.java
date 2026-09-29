package com.fueldispatch.dispatch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** API-1.5, API-3.1, API-3.2 against a real PostgreSQL whose schema comes only from Flyway. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaOrderRepositoryAdapter.class, OrderJpaMapper.class})
class OrderPersistenceIT {

    private static final Instant T0 = Instant.parse("2026-09-28T10:15:30Z");

    private static final Vessel VESSEL = new Vessel("Nordic Star", "9321483");
    private static final Berth BERTH = new Berth("B-03");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("850.5"));
    private static final DeliveryWindow WINDOW =
            new DeliveryWindow(
                    Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T14:00:00Z"));

    @Autowired private OrderRepository orderRepository;
    @Autowired private TestEntityManager entityManager;

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static DispatchOrder newOrder(Instant createdAt) {
        return DispatchOrder.create(
                VESSEL, BERTH, FuelType.VLSFO, QUANTITY, WINDOW, clockAt(createdAt));
    }

    private DispatchOrder saveAt(Instant createdAt, OrderStatus status) {
        DispatchOrder order = newOrder(createdAt);
        switch (status) {
            case CREATED -> {}
            case APPROVED -> order.approve(clockAt(createdAt));
            case CANCELLED -> order.cancel(new CancellationReason("No berth"), clockAt(createdAt));
            default -> throw new IllegalArgumentException("unsupported in this test: " + status);
        }
        return orderRepository.save(order);
    }

    /** Forces the next read to hit the database instead of the persistence context. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // API-3.1, API-3.2
    @Test
    void save_newOrder_findByIdRestoresEveryField() {
        DispatchOrder order = newOrder(T0);

        orderRepository.save(order);
        flushAndClear();

        DispatchOrder loaded = orderRepository.findById(order.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(order.id());
        assertThat(loaded.vessel()).isEqualTo(VESSEL);
        assertThat(loaded.berth()).isEqualTo(BERTH);
        assertThat(loaded.fuelType()).isEqualTo(FuelType.VLSFO);
        assertThat(loaded.quantity()).isEqualTo(QUANTITY);
        assertThat(loaded.deliveryWindow()).isEqualTo(WINDOW);
        assertThat(loaded.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(loaded.createdAt()).isEqualTo(T0);
        assertThat(loaded.updatedAt()).isEqualTo(T0);
        assertThat(loaded.cancellationReason()).isEmpty();
        assertThat(loaded.domainEvents()).isEmpty();
    }

    @Test
    void save_existingOrderAfterCancel_updatesStatusReasonAndUpdatedAt() {
        DispatchOrder order = newOrder(T0);
        orderRepository.save(order);
        flushAndClear();
        Instant later = T0.plusSeconds(3600);

        DispatchOrder loaded = orderRepository.findById(order.id()).orElseThrow();
        loaded.cancel(new CancellationReason("Vessel delayed"), clockAt(later));
        orderRepository.save(loaded);
        flushAndClear();

        DispatchOrder reloaded = orderRepository.findById(order.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reloaded.cancellationReason())
                .contains(new CancellationReason("Vessel delayed"));
        assertThat(reloaded.createdAt()).isEqualTo(T0);
        assertThat(reloaded.updatedAt()).isEqualTo(later);
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertThat(orderRepository.findById(OrderId.newId())).isEmpty();
    }

    // API-1.5
    @Test
    void findPage_byStatus_returnsOnlyThatStatusNewestFirst() {
        DispatchOrder oldest = saveAt(T0, OrderStatus.APPROVED);
        saveAt(T0.plusSeconds(60), OrderStatus.CREATED);
        DispatchOrder middle = saveAt(T0.plusSeconds(120), OrderStatus.APPROVED);
        DispatchOrder newest = saveAt(T0.plusSeconds(180), OrderStatus.APPROVED);
        flushAndClear();

        OrderPage first = orderRepository.findPage(Optional.of(OrderStatus.APPROVED), 0, 2);
        OrderPage second = orderRepository.findPage(Optional.of(OrderStatus.APPROVED), 1, 2);

        assertThat(first.items())
                .extracting(DispatchOrder::id)
                .containsExactly(newest.id(), middle.id());
        assertThat(second.items()).extracting(DispatchOrder::id).containsExactly(oldest.id());
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.page()).isEqualTo(1);
    }

    // API-1.5
    @Test
    void findPage_withoutStatus_returnsEveryStatusNewestFirst() {
        DispatchOrder created = saveAt(T0, OrderStatus.CREATED);
        DispatchOrder cancelled = saveAt(T0.plusSeconds(60), OrderStatus.CANCELLED);
        DispatchOrder approved = saveAt(T0.plusSeconds(120), OrderStatus.APPROVED);
        flushAndClear();

        OrderPage page = orderRepository.findPage(Optional.empty(), 0, 20);

        assertThat(page.items())
                .extracting(DispatchOrder::id)
                .containsExactly(approved.id(), cancelled.id(), created.id());
        assertThat(page.items().get(1).cancellationReason())
                .contains(new CancellationReason("No berth"));
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void findPage_sameCreatedAt_ordersByIdDescendingForStablePages() {
        DispatchOrder first = saveAt(T0, OrderStatus.CREATED);
        DispatchOrder second = saveAt(T0, OrderStatus.CREATED);
        flushAndClear();

        OrderPage page = orderRepository.findPage(Optional.empty(), 0, 20);

        // PostgreSQL orders uuid by unsigned bytes, i.e. like the lower-case hex strings
        // (UUID.compareTo compares signed longs and can disagree).
        OrderId higher =
                first.id().toString().compareTo(second.id().toString()) > 0
                        ? first.id()
                        : second.id();
        assertThat(page.items().get(0).id()).isEqualTo(higher);
    }
}
