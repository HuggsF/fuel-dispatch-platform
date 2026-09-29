package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The order was delivered (DOM-2.3). Carries the order data at the time of the change (DOM-3.2).
 */
public record OrderDelivered(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        OrderStatus status,
        Vessel vessel,
        Berth berth,
        FuelType fuelType,
        Quantity quantity)
        implements DomainEvent {}
