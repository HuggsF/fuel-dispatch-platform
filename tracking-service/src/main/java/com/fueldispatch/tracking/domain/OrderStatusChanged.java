package com.fueldispatch.tracking.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A status change of an order, as received from the event stream. {@code reason} is present only
 * for {@link TrackingStatus#CANCELLED}.
 */
public record OrderStatusChanged(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        TrackingStatus status,
        OrderSummary summary,
        String reason) {

    public OrderStatusChanged {
        requirePresent(eventId, "event id");
        requirePresent(orderId, "order id");
        requirePresent(occurredAt, "occurredAt");
        requirePresent(status, "status");
        requirePresent(summary, "summary");
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new DomainValidationException(name + " is required");
        }
    }
}
