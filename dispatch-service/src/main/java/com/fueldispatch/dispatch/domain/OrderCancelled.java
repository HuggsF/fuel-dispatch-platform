package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The order was cancelled (DOM-2.4). Carries the order data at the time of the change (DOM-3.2).
 * Also carries the reason.
 */
public record OrderCancelled(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        OrderStatus status,
        Vessel vessel,
        Berth berth,
        FuelType fuelType,
        Quantity quantity,
        CancellationReason reason)
        implements DomainEvent {}
