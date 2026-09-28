package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/** The order was dispatched (DOM-2.2). */
public record OrderDispatched(UUID eventId, OrderId orderId, Instant occurredAt, OrderStatus status)
        implements DomainEvent {}
