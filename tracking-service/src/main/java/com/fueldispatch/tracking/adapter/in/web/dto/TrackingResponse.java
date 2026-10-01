package com.fueldispatch.tracking.adapter.in.web.dto;

import com.fueldispatch.tracking.domain.HistoryEntry;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The tracking of one order: current status, order summary and history by occurredAt (TRK-2.1). */
public record TrackingResponse(
        UUID orderId,
        TrackingStatus currentStatus,
        Instant lastOccurredAt,
        String vesselName,
        String vesselImo,
        String berth,
        String fuelType,
        BigDecimal quantityM3,
        List<HistoryItem> history) {

    /** One status change; {@code reason} is null unless it is a cancellation. */
    public record HistoryItem(
            UUID eventId, TrackingStatus status, Instant occurredAt, String reason) {

        static HistoryItem from(HistoryEntry entry) {
            return new HistoryItem(
                    entry.eventId(), entry.status(), entry.occurredAt(), entry.reason());
        }
    }

    public static TrackingResponse from(OrderTracking tracking) {
        return new TrackingResponse(
                tracking.orderId(),
                tracking.currentStatus(),
                tracking.lastOccurredAt(),
                tracking.summary().vesselName(),
                tracking.summary().vesselImo(),
                tracking.summary().berth(),
                tracking.summary().fuelType(),
                tracking.summary().quantityM3(),
                tracking.history().stream().map(HistoryItem::from).toList());
    }
}
