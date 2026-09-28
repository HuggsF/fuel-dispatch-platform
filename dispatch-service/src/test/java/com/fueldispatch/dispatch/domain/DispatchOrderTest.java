package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

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

    /** DOM-2.1 to DOM-2.5 and DOM-2.7: lifecycle transitions. */
    @Nested
    class Transitions {

        private static final Instant LATER = NOW.plusSeconds(600);
        private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);
        private static final CancellationReason REASON = new CancellationReason("Vessel delayed");

        /** Brings a new order to {@code status} through the allowed transitions. */
        private static DispatchOrder orderIn(OrderStatus status) {
            DispatchOrder order = newOrder();
            switch (status) {
                case CREATED -> {}
                case APPROVED -> order.approve(CLOCK);
                case DISPATCHED -> {
                    order.approve(CLOCK);
                    order.dispatch(CLOCK);
                }
                case DELIVERED -> {
                    order.approve(CLOCK);
                    order.dispatch(CLOCK);
                    order.deliver(CLOCK);
                }
                case CANCELLED -> order.cancel(REASON, CLOCK);
            }
            return order;
        }

        private static void apply(DispatchOrder order, OrderAction action) {
            switch (action) {
                case APPROVE -> order.approve(LATER_CLOCK);
                case DISPATCH -> order.dispatch(LATER_CLOCK);
                case DELIVER -> order.deliver(LATER_CLOCK);
                case CANCEL -> order.cancel(REASON, LATER_CLOCK);
            }
        }

        @Test
        void approve_createdOrder_becomesApprovedAndRegistersOrderApproved() {
            DispatchOrder order = newOrder();

            order.approve(LATER_CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.APPROVED);
            assertThat(order.updatedAt()).isEqualTo(LATER);
            assertThat(order.createdAt()).isEqualTo(NOW);
            assertThat(order.domainEvents()).hasSize(2);
            assertThat(order.domainEvents().getLast())
                    .isInstanceOfSatisfying(
                            OrderApproved.class,
                            event -> {
                                assertThat(event.eventId()).isNotNull();
                                assertThat(event.orderId()).isEqualTo(order.id());
                                assertThat(event.occurredAt()).isEqualTo(LATER);
                                assertThat(event.status()).isEqualTo(OrderStatus.APPROVED);
                            });
        }

        @Test
        void dispatch_approvedOrder_becomesDispatchedAndRegistersOrderDispatched() {
            DispatchOrder order = orderIn(OrderStatus.APPROVED);

            order.dispatch(LATER_CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED);
            assertThat(order.updatedAt()).isEqualTo(LATER);
            assertThat(order.domainEvents().getLast())
                    .isInstanceOfSatisfying(
                            OrderDispatched.class,
                            event -> {
                                assertThat(event.eventId()).isNotNull();
                                assertThat(event.orderId()).isEqualTo(order.id());
                                assertThat(event.occurredAt()).isEqualTo(LATER);
                                assertThat(event.status()).isEqualTo(OrderStatus.DISPATCHED);
                            });
        }

        @Test
        void deliver_dispatchedOrder_becomesDeliveredAndRegistersOrderDelivered() {
            DispatchOrder order = orderIn(OrderStatus.DISPATCHED);

            order.deliver(LATER_CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.updatedAt()).isEqualTo(LATER);
            assertThat(order.domainEvents().getLast())
                    .isInstanceOfSatisfying(
                            OrderDelivered.class,
                            event -> {
                                assertThat(event.eventId()).isNotNull();
                                assertThat(event.orderId()).isEqualTo(order.id());
                                assertThat(event.occurredAt()).isEqualTo(LATER);
                                assertThat(event.status()).isEqualTo(OrderStatus.DELIVERED);
                            });
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CREATED", "APPROVED"})
        void cancel_createdOrApprovedOrder_becomesCancelledWithReasonAndRegistersOrderCancelled(
                OrderStatus from) {
            DispatchOrder order = orderIn(from);

            order.cancel(REASON, LATER_CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellationReason()).contains(REASON);
            assertThat(order.updatedAt()).isEqualTo(LATER);
            assertThat(order.domainEvents().getLast())
                    .isInstanceOfSatisfying(
                            OrderCancelled.class,
                            event -> {
                                assertThat(event.eventId()).isNotNull();
                                assertThat(event.orderId()).isEqualTo(order.id());
                                assertThat(event.occurredAt()).isEqualTo(LATER);
                                assertThat(event.status()).isEqualTo(OrderStatus.CANCELLED);
                                assertThat(event.reason()).isEqualTo(REASON);
                            });
        }

        @Test
        void cancellationReason_notCancelledOrder_isEmpty() {
            assertThat(newOrder().cancellationReason()).isEmpty();
        }

        @Test
        void fullLifecycle_registersEventsInOrder() {
            DispatchOrder order = orderIn(OrderStatus.DELIVERED);

            assertThat(order.domainEvents())
                    .satisfiesExactly(
                            event -> assertThat(event).isInstanceOf(OrderCreated.class),
                            event -> assertThat(event).isInstanceOf(OrderApproved.class),
                            event -> assertThat(event).isInstanceOf(OrderDispatched.class),
                            event -> assertThat(event).isInstanceOf(OrderDelivered.class));
        }

        @ParameterizedTest(name = "{0} --{1}--> rejected")
        @CsvSource({
            "CREATED,    DISPATCH",
            "CREATED,    DELIVER",
            "APPROVED,   APPROVE",
            "APPROVED,   DELIVER",
            "DISPATCHED, APPROVE",
            "DISPATCHED, DISPATCH",
            "DISPATCHED, CANCEL",
            "DELIVERED,  APPROVE",
            "DELIVERED,  DISPATCH",
            "DELIVERED,  DELIVER",
            "DELIVERED,  CANCEL",
            "CANCELLED,  APPROVE",
            "CANCELLED,  DISPATCH",
            "CANCELLED,  DELIVER",
            "CANCELLED,  CANCEL"
        })
        void invalidTransition_throwsAndLeavesStateAndEventsUntouched(
                OrderStatus from, OrderAction action) {
            DispatchOrder order = orderIn(from);
            Instant updatedAtBefore = order.updatedAt();
            Optional<CancellationReason> reasonBefore = order.cancellationReason();
            List<DomainEvent> eventsBefore = order.domainEvents();

            assertThatThrownBy(() -> apply(order, action))
                    .isInstanceOfSatisfying(
                            InvalidOrderTransitionException.class,
                            exception -> {
                                assertThat(exception.currentStatus()).isEqualTo(from);
                                assertThat(exception.action()).isEqualTo(action);
                                assertThat(exception.getMessage())
                                        .contains(from.name())
                                        .contains(action.name());
                            });

            assertThat(order.status()).isEqualTo(from);
            assertThat(order.updatedAt()).isEqualTo(updatedAtBefore);
            assertThat(order.cancellationReason()).isEqualTo(reasonBefore);
            assertThat(order.domainEvents()).isEqualTo(eventsBefore);
        }

        @Test
        void cancel_missingReason_throwsDomainValidationExceptionAndLeavesStateUntouched() {
            DispatchOrder order = newOrder();

            assertThatThrownBy(() -> order.cancel(null, LATER_CLOCK))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("cancellation reason");

            assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.domainEvents()).hasSize(1);
        }

        @Test
        void transition_missingClock_throwsNullPointerException() {
            DispatchOrder order = newOrder();

            assertThatThrownBy(() -> order.approve(null)).isInstanceOf(NullPointerException.class);
            assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        }
    }

    /** DOM-3.1: events are handed over once, in order. */
    @Nested
    class PullDomainEvents {

        @Test
        void pullDomainEvents_afterTransitions_returnsEventsInOrderAndClearsThem() {
            DispatchOrder order = newOrder();
            order.approve(CLOCK);

            List<DomainEvent> pulled = order.pullDomainEvents();

            assertThat(pulled)
                    .satisfiesExactly(
                            event -> assertThat(event).isInstanceOf(OrderCreated.class),
                            event -> assertThat(event).isInstanceOf(OrderApproved.class));
            assertThat(order.domainEvents()).isEmpty();
            assertThat(order.pullDomainEvents()).isEmpty();
        }

        @Test
        void pullDomainEvents_thenNewTransition_returnsOnlyTheNewEvent() {
            DispatchOrder order = newOrder();
            order.pullDomainEvents();

            order.approve(CLOCK);

            assertThat(order.pullDomainEvents()).singleElement().isInstanceOf(OrderApproved.class);
        }

        @Test
        void pullDomainEvents_returnedList_isNotAffectedByLaterTransitions() {
            DispatchOrder order = newOrder();
            List<DomainEvent> pulled = order.pullDomainEvents();

            order.approve(CLOCK);

            assertThat(pulled).singleElement().isInstanceOf(OrderCreated.class);
            assertThatThrownBy(() -> pulled.add(order.domainEvents().getFirst()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    /** Rebuilding a persisted order: state restored, no events registered. */
    @Nested
    class Rehydrate {

        private static final OrderId ID = OrderId.newId();
        private static final Instant UPDATED = NOW.plusSeconds(900);
        private static final CancellationReason REASON = new CancellationReason("Vessel delayed");

        private static DispatchOrder rehydrate(
                OrderStatus status, CancellationReason cancellationReason) {
            return DispatchOrder.rehydrate(
                    ID,
                    VESSEL,
                    BERTH,
                    FuelType.HFO,
                    QUANTITY,
                    WINDOW,
                    status,
                    NOW,
                    UPDATED,
                    cancellationReason);
        }

        @Test
        void rehydrate_validState_restoresEveryFieldWithoutEvents() {
            DispatchOrder order = rehydrate(OrderStatus.DISPATCHED, null);

            assertThat(order.id()).isEqualTo(ID);
            assertThat(order.vessel()).isEqualTo(VESSEL);
            assertThat(order.berth()).isEqualTo(BERTH);
            assertThat(order.fuelType()).isEqualTo(FuelType.HFO);
            assertThat(order.quantity()).isEqualTo(QUANTITY);
            assertThat(order.deliveryWindow()).isEqualTo(WINDOW);
            assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED);
            assertThat(order.createdAt()).isEqualTo(NOW);
            assertThat(order.updatedAt()).isEqualTo(UPDATED);
            assertThat(order.cancellationReason()).isEmpty();
            assertThat(order.domainEvents()).isEmpty();
        }

        @Test
        void rehydrate_cancelledWithReason_restoresReason() {
            DispatchOrder order = rehydrate(OrderStatus.CANCELLED, REASON);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellationReason()).contains(REASON);
            assertThat(order.domainEvents()).isEmpty();
        }

        @Test
        void rehydrate_thenTransition_registersOnlyTheNewEvent() {
            DispatchOrder order = rehydrate(OrderStatus.APPROVED, null);

            order.dispatch(CLOCK);

            assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED);
            assertThat(order.pullDomainEvents())
                    .singleElement()
                    .isInstanceOf(OrderDispatched.class);
        }

        @Test
        void rehydrate_cancelledWithoutReason_throwsDomainValidationException() {
            assertThatThrownBy(() -> rehydrate(OrderStatus.CANCELLED, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("cancellation reason");
        }

        @ParameterizedTest
        @EnumSource(
                value = OrderStatus.class,
                names = {"CREATED", "APPROVED", "DISPATCHED", "DELIVERED"})
        void rehydrate_notCancelledWithReason_throwsDomainValidationException(OrderStatus status) {
            assertThatThrownBy(() -> rehydrate(status, REASON))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("cancellation reason");
        }

        @Test
        void rehydrate_missingId_throwsDomainValidationException() {
            assertThatThrownBy(
                            () ->
                                    DispatchOrder.rehydrate(
                                            null,
                                            VESSEL,
                                            BERTH,
                                            FuelType.HFO,
                                            QUANTITY,
                                            WINDOW,
                                            OrderStatus.CREATED,
                                            NOW,
                                            NOW,
                                            null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("order id");
        }

        @Test
        void rehydrate_missingStatus_throwsDomainValidationException() {
            assertThatThrownBy(() -> rehydrate(null, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("status");
        }

        @Test
        void rehydrate_missingTimestamps_throwsDomainValidationException() {
            assertThatThrownBy(
                            () ->
                                    DispatchOrder.rehydrate(
                                            ID,
                                            VESSEL,
                                            BERTH,
                                            FuelType.HFO,
                                            QUANTITY,
                                            WINDOW,
                                            OrderStatus.CREATED,
                                            null,
                                            NOW,
                                            null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("createdAt");
            assertThatThrownBy(
                            () ->
                                    DispatchOrder.rehydrate(
                                            ID,
                                            VESSEL,
                                            BERTH,
                                            FuelType.HFO,
                                            QUANTITY,
                                            WINDOW,
                                            OrderStatus.CREATED,
                                            NOW,
                                            null,
                                            null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("updatedAt");
        }

        @Test
        void rehydrate_missingOrderData_throwsDomainValidationException() {
            assertThatThrownBy(
                            () ->
                                    DispatchOrder.rehydrate(
                                            ID,
                                            null,
                                            BERTH,
                                            FuelType.HFO,
                                            QUANTITY,
                                            WINDOW,
                                            OrderStatus.CREATED,
                                            NOW,
                                            NOW,
                                            null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("vessel");
        }
    }
}
