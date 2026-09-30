package com.fueldispatch.tracking.adapter.in.web.dto;

import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Data of one {@code status-changed} Server-Sent Event (TRK-3.1): the applied change and the order
 * summary, so a client can render it without another request. {@code reason} is null unless
 * cancelled.
 */
public record StatusChangeEvent(
        UUID eventId,
        UUID orderId,
        TrackingStatus status,
        Instant occurredAt,
        String reason,
        String vesselName,
        String vesselImo,
        String berth,
        String fuelType,
        BigDecimal quantityM3) {

    public static StatusChangeEvent from(OrderStatusChanged change) {
        return new StatusChangeEvent(
                change.eventId(),
                change.orderId(),
                change.status(),
                change.occurredAt(),
                change.reason(),
                change.summary().vesselName(),
                change.summary().vesselImo(),
                change.summary().berth(),
                change.summary().fuelType(),
                change.summary().quantityM3());
    }
}
