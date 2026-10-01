package com.fueldispatch.tracking.adapter.in.web.dto;

import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tracking without its history, as streamed by the status filter (TRK-2.3). */
public record TrackingSummaryResponse(
        UUID orderId,
        TrackingStatus currentStatus,
        Instant lastOccurredAt,
        String vesselName,
        String vesselImo,
        String berth,
        String fuelType,
        BigDecimal quantityM3) {

    public static TrackingSummaryResponse from(OrderTracking tracking) {
        return new TrackingSummaryResponse(
                tracking.orderId(),
                tracking.currentStatus(),
                tracking.lastOccurredAt(),
                tracking.summary().vesselName(),
                tracking.summary().vesselImo(),
                tracking.summary().berth(),
                tracking.summary().fuelType(),
                tracking.summary().quantityM3());
    }
}
