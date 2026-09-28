package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DispatchOrderTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Vessel VESSEL = new Vessel("Nordic Star", "9321483");
    private static final Berth BERTH = new Berth("B-03");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("850.5"));
    private static final DeliveryWindow WINDOW =
            new DeliveryWindow(
                    Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T14:00:00Z"));

    private static DispatchOrder newOrder() {
        return DispatchOrder.create(VESSEL, BERTH, FuelType.VLSFO, QUANTITY, WINDOW, CLOCK);
    }

    // DOM-1.1

    @Test
    void create_validData_setsIdStatusAndTimestampsFromClock() {
        DispatchOrder order = newOrder();

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.createdAt()).isEqualTo(NOW);
        assertThat(order.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void create_validData_keepsOrderData() {
        DispatchOrder order = newOrder();

        assertThat(order.vessel()).isEqualTo(VESSEL);
        assertThat(order.berth()).isEqualTo(BERTH);
        assertThat(order.fuelType()).isEqualTo(FuelType.VLSFO);
        assertThat(order.quantity()).isEqualTo(QUANTITY);
        assertThat(order.deliveryWindow()).isEqualTo(WINDOW);
    }

    @Test
    void create_calledTwice_assignsDistinctIds() {
        assertThat(newOrder().id()).isNotEqualTo(newOrder().id());
    }

    @Test
    void create_missingVessel_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                DispatchOrder.create(
                                        null, BERTH, FuelType.MGO, QUANTITY, WINDOW, CLOCK))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("vessel");
    }

    @Test
    void create_missingBerth_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                DispatchOrder.create(
                                        VESSEL, null, FuelType.MGO, QUANTITY, WINDOW, CLOCK))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("berth");
    }

    @Test
    void create_missingFuelType_throwsDomainValidationException() {
        assertThatThrownBy(() -> DispatchOrder.create(VESSEL, BERTH, null, QUANTITY, WINDOW, CLOCK))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("fuel type");
    }

    @Test
    void create_missingQuantity_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                DispatchOrder.create(
                                        VESSEL, BERTH, FuelType.MGO, null, WINDOW, CLOCK))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void create_missingDeliveryWindow_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                DispatchOrder.create(
                                        VESSEL, BERTH, FuelType.MGO, QUANTITY, null, CLOCK))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("delivery window");
    }

    @Test
    void create_missingClock_throwsNullPointerException() {
        assertThatThrownBy(
                        () ->
                                DispatchOrder.create(
                                        VESSEL, BERTH, FuelType.MGO, QUANTITY, WINDOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    // DOM-1.6, DOM-3.2

    @Test
    void create_validData_registersOneOrderCreatedWithOrderSnapshot() {
        DispatchOrder order = newOrder();

        assertThat(order.domainEvents())
                .singleElement()
                .isInstanceOfSatisfying(
                        OrderCreated.class,
                        event -> {
                            assertThat(event.eventId()).isNotNull();
                            assertThat(event.orderId()).isEqualTo(order.id());
                            assertThat(event.occurredAt()).isEqualTo(NOW);
                            assertThat(event.status()).isEqualTo(OrderStatus.CREATED);
                            assertThat(event.vessel()).isEqualTo(VESSEL);
                            assertThat(event.berth()).isEqualTo(BERTH);
                            assertThat(event.fuelType()).isEqualTo(FuelType.VLSFO);
                            assertThat(event.quantity()).isEqualTo(QUANTITY);
                            assertThat(event.deliveryWindow()).isEqualTo(WINDOW);
                        });
    }

    @Test
    void create_twoOrders_eventsHaveDistinctEventIds() {
        DomainEvent first = newOrder().domainEvents().getFirst();
        DomainEvent second = newOrder().domainEvents().getFirst();

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    void domainEvents_isReadOnly() {
        DispatchOrder order = newOrder();

        assertThatThrownBy(() -> order.domainEvents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.domainEvents()).hasSize(1);
    }
}
