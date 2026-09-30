package com.fueldispatch.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTrackingTest {

    private static final UUID ORDER_ID = UUID.fromString("1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10");
    private static final OrderSummary SUMMARY =
            new OrderSummary(
                    "MV Atlantic Star", "9321483", "B-03", "VLSFO", new BigDecimal("850.125"));

    private static final Instant T0 = Instant.parse("2026-10-05T13:58:10Z");
    private static final Instant T1 = Instant.parse("2026-10-05T14:03:22Z");
    private static final Instant T2 = Instant.parse("2026-10-05T14:40:05Z");

    private static OrderStatusChanged event(TrackingStatus status, Instant occurredAt) {
        return event(UUID.randomUUID(), status, occurredAt);
    }

    private static OrderStatusChanged event(UUID eventId, TrackingStatus status, Instant at) {
        return new OrderStatusChanged(eventId, ORDER_ID, at, status, SUMMARY, null);
    }

    // TRK-1.1

    @Test
    void apply_firstEvent_setsStatusSummaryAndHistory() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        OrderStatusChanged created = event(TrackingStatus.CREATED, T0);

        ApplyResult result = tracking.apply(created);

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(tracking.orderId()).isEqualTo(ORDER_ID);
        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.CREATED);
        assertThat(tracking.summary()).isEqualTo(SUMMARY);
        assertThat(tracking.lastOccurredAt()).isEqualTo(T0);
        assertThat(tracking.history())
                .containsExactly(
                        new HistoryEntry(created.eventId(), TrackingStatus.CREATED, T0, null));
        assertThat(tracking.processedEventIds()).containsExactly(created.eventId());
    }

    @Test
    void forOrder_beforeAnyEvent_hasNoStatusAndEmptyHistory() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);

        assertThat(tracking.currentStatus()).isNull();
        assertThat(tracking.summary()).isNull();
        assertThat(tracking.lastOccurredAt()).isNull();
        assertThat(tracking.history()).isEmpty();
        assertThat(tracking.processedEventIds()).isEmpty();
    }

    @Test
    void apply_newerEvent_updatesStatusAndAppendsHistory() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.CREATED, T0));

        ApplyResult result = tracking.apply(event(TrackingStatus.APPROVED, T1));

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.APPROVED);
        assertThat(tracking.lastOccurredAt()).isEqualTo(T1);
        assertThat(tracking.history())
                .extracting(HistoryEntry::status)
                .containsExactly(TrackingStatus.CREATED, TrackingStatus.APPROVED);
    }

    @Test
    void apply_eventAtSameInstantAsLatest_isApplied() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.CREATED, T0));

        ApplyResult result = tracking.apply(event(TrackingStatus.APPROVED, T0));

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.APPROVED);
    }

    @Test
    void apply_cancelledEvent_keepsReasonInHistory() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        UUID eventId = UUID.randomUUID();

        tracking.apply(
                new OrderStatusChanged(
                        eventId, ORDER_ID, T0, TrackingStatus.CANCELLED, SUMMARY, "Vessel left"));

        assertThat(tracking.history())
                .containsExactly(
                        new HistoryEntry(eventId, TrackingStatus.CANCELLED, T0, "Vessel left"));
    }

    // TRK-1.2

    @Test
    void apply_alreadyProcessedEventId_returnsDuplicateAndChangesNothing() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        OrderStatusChanged created = event(TrackingStatus.CREATED, T0);
        tracking.apply(created);

        ApplyResult result = tracking.apply(created);

        assertThat(result).isEqualTo(ApplyResult.DUPLICATE);
        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.CREATED);
        assertThat(tracking.history()).hasSize(1);
        assertThat(tracking.processedEventIds()).containsExactly(created.eventId());
    }

    @Test
    void apply_redeliveredOutOfOrderEvent_returnsDuplicate() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.APPROVED, T1));
        OrderStatusChanged late = event(TrackingStatus.CREATED, T0);
        tracking.apply(late);

        ApplyResult result = tracking.apply(late);

        assertThat(result).isEqualTo(ApplyResult.DUPLICATE);
        assertThat(tracking.history()).hasSize(2);
    }

    @Test
    void apply_moreThanFiftyEvents_keepsOnlyTheLastFiftyIds() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < OrderTracking.MAX_PROCESSED_EVENT_IDS + 5; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            tracking.apply(event(id, TrackingStatus.CREATED, T0.plusSeconds(i)));
        }

        assertThat(tracking.processedEventIds())
                .hasSize(OrderTracking.MAX_PROCESSED_EVENT_IDS)
                .containsExactlyElementsOf(ids.subList(5, ids.size()));
    }

    // TRK-1.3

    @Test
    void apply_olderEvent_recordsHistoryButKeepsCurrentStatus() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.DISPATCHED, T2));

        ApplyResult result = tracking.apply(event(TrackingStatus.APPROVED, T1));

        assertThat(result).isEqualTo(ApplyResult.OUT_OF_ORDER_RECORDED);
        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.DISPATCHED);
        assertThat(tracking.lastOccurredAt()).isEqualTo(T2);
        assertThat(tracking.processedEventIds()).hasSize(2);
    }

    @Test
    void history_eventsArrivingOutOfOrder_isSortedByOccurredAt() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.DISPATCHED, T2));
        tracking.apply(event(TrackingStatus.CREATED, T0));
        tracking.apply(event(TrackingStatus.APPROVED, T1));

        assertThat(tracking.history())
                .extracting(HistoryEntry::occurredAt)
                .containsExactly(T0, T1, T2);
    }

    // Guards and rehydration

    @Test
    void apply_eventOfAnotherOrder_throwsDomainValidationException() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        OrderStatusChanged other =
                new OrderStatusChanged(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        T0,
                        TrackingStatus.CREATED,
                        SUMMARY,
                        null);

        assertThatThrownBy(() -> tracking.apply(other))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("order");
    }

    @Test
    void apply_nullEvent_throwsDomainValidationException() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);

        assertThatThrownBy(() -> tracking.apply(null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void forOrder_nullId_throwsDomainValidationException() {
        assertThatThrownBy(() -> OrderTracking.forOrder(null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("order id");
    }

    @Test
    void rehydrate_restoresStateAndKeepsDeduplicating() {
        UUID seen = UUID.randomUUID();
        HistoryEntry entry = new HistoryEntry(seen, TrackingStatus.APPROVED, T1, null);

        OrderTracking tracking =
                OrderTracking.rehydrate(
                        ORDER_ID,
                        SUMMARY,
                        TrackingStatus.APPROVED,
                        T1,
                        List.of(entry),
                        List.of(seen));

        assertThat(tracking.currentStatus()).isEqualTo(TrackingStatus.APPROVED);
        assertThat(tracking.summary()).isEqualTo(SUMMARY);
        assertThat(tracking.lastOccurredAt()).isEqualTo(T1);
        assertThat(tracking.history()).containsExactly(entry);
        assertThat(tracking.apply(event(seen, TrackingStatus.APPROVED, T1)))
                .isEqualTo(ApplyResult.DUPLICATE);
    }

    @Test
    void history_returnedList_isReadOnly() {
        OrderTracking tracking = OrderTracking.forOrder(ORDER_ID);
        tracking.apply(event(TrackingStatus.CREATED, T0));

        assertThatThrownBy(() -> tracking.history().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tracking.processedEventIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
