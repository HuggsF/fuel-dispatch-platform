package com.fueldispatch.tracking.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.UUID;

/**
 * Aggregate root of the read side: the current status and the history of one order, built from its
 * events. Idempotent (TRK-1.2) and tolerant of out-of-order delivery (TRK-1.3).
 *
 * <p>{@code currentStatus}, {@code summary} and {@code lastOccurredAt} are {@code null} only before
 * the first event is applied.
 */
public class OrderTracking {

    /** How many event ids are remembered for deduplication; the oldest are evicted first. */
    public static final int MAX_PROCESSED_EVENT_IDS = 50;

    private static final Comparator<HistoryEntry> BY_OCCURRED_AT =
            Comparator.comparing(HistoryEntry::occurredAt);

    private final UUID orderId;
    private OrderSummary summary;
    private TrackingStatus currentStatus;
    private Instant lastOccurredAt;
    private final List<HistoryEntry> history;
    private final SequencedSet<UUID> processedEventIds;

    private OrderTracking(
            UUID orderId,
            OrderSummary summary,
            TrackingStatus currentStatus,
            Instant lastOccurredAt,
            List<HistoryEntry> history,
            List<UUID> processedEventIds) {
        if (orderId == null) {
            throw new DomainValidationException("order id is required");
        }
        this.orderId = orderId;
        this.summary = summary;
        this.currentStatus = currentStatus;
        this.lastOccurredAt = lastOccurredAt;
        this.history = new ArrayList<>(history);
        this.history.sort(BY_OCCURRED_AT);
        this.processedEventIds = new LinkedHashSet<>(processedEventIds);
    }

    /** A tracking with no events yet, for the first event of an order. */
    public static OrderTracking forOrder(UUID orderId) {
        return new OrderTracking(orderId, null, null, null, List.of(), List.of());
    }

    /** Rebuilds a persisted tracking. {@code processedEventIds} is ordered oldest first. */
    public static OrderTracking rehydrate(
            UUID orderId,
            OrderSummary summary,
            TrackingStatus currentStatus,
            Instant lastOccurredAt,
            List<HistoryEntry> history,
            List<UUID> processedEventIds) {
        return new OrderTracking(
                orderId, summary, currentStatus, lastOccurredAt, history, processedEventIds);
    }

    /**
     * Applies an event of this order. A known event id is a {@link ApplyResult#DUPLICATE}; an event
     * strictly older than the latest applied one only goes to history; anything else (including the
     * same instant) becomes the current status.
     */
    public ApplyResult apply(OrderStatusChanged event) {
        if (event == null) {
            throw new DomainValidationException("event is required");
        }
        if (!orderId.equals(event.orderId())) {
            throw new DomainValidationException(
                    "event of order " + event.orderId() + " applied to order " + orderId);
        }
        if (processedEventIds.contains(event.eventId())) {
            return ApplyResult.DUPLICATE;
        }

        rememberEventId(event.eventId());
        addToHistory(event);

        if (lastOccurredAt != null && event.occurredAt().isBefore(lastOccurredAt)) {
            return ApplyResult.OUT_OF_ORDER_RECORDED;
        }
        currentStatus = event.status();
        summary = event.summary();
        lastOccurredAt = event.occurredAt();
        return ApplyResult.APPLIED;
    }

    private void rememberEventId(UUID eventId) {
        processedEventIds.addLast(eventId);
        while (processedEventIds.size() > MAX_PROCESSED_EVENT_IDS) {
            processedEventIds.removeFirst();
        }
    }

    /** Inserts after every entry with the same or an earlier instant, keeping history sorted. */
    private void addToHistory(OrderStatusChanged event) {
        HistoryEntry entry =
                new HistoryEntry(
                        event.eventId(), event.status(), event.occurredAt(), event.reason());
        int index = history.size();
        while (index > 0 && history.get(index - 1).occurredAt().isAfter(entry.occurredAt())) {
            index--;
        }
        history.add(index, entry);
    }

    public UUID orderId() {
        return orderId;
    }

    public OrderSummary summary() {
        return summary;
    }

    public TrackingStatus currentStatus() {
        return currentStatus;
    }

    public Instant lastOccurredAt() {
        return lastOccurredAt;
    }

    /** History sorted by {@code occurredAt} (TRK-2.1), read-only. */
    public List<HistoryEntry> history() {
        return List.copyOf(history);
    }

    /** Remembered event ids, oldest first, read-only. */
    public List<UUID> processedEventIds() {
        return List.copyOf(processedEventIds);
    }
}
