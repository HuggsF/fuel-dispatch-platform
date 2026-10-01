package com.fueldispatch.tracking.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The v1 event envelope ({@code contracts/dispatch-order-event.v1.schema.json}) as read by this
 * service. Written from the JSON contract only, never from dispatch-service code (TRK-4.2). Unknown
 * fields are ignored, as the contract requires of consumers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrderEventMessage(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID orderId,
        Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(
            TrackingStatus status,
            String vesselName,
            String vesselImo,
            String berth,
            String fuelType,
            BigDecimal quantityM3,
            String reason) {}

    /** Throws {@code DomainValidationException} when a required field is missing. */
    OrderStatusChanged toDomain() {
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
        return new OrderStatusChanged(
                eventId,
                orderId,
                occurredAt,
                data.status(),
                new OrderSummary(
                        data.vesselName(),
                        data.vesselImo(),
                        data.berth(),
                        data.fuelType(),
                        data.quantityM3()),
                data.reason());
    }
}
