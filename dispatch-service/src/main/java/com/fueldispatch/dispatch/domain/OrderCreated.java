package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/** A new order was registered (DOM-1.6). Carries the full order snapshot for consumers. */
public record OrderCreated(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        OrderStatus status,
        Vessel vessel,
        Berth berth,
        FuelType fuelType,
        Quantity quantity,
        DeliveryWindow deliveryWindow)
        implements DomainEvent {}
