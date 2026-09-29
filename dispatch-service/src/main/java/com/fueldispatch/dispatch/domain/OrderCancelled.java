package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/** The order was cancelled (DOM-2.4); carries the reason. */
public record OrderCancelled(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        OrderStatus status,
        CancellationReason reason)
        implements DomainEvent {}
