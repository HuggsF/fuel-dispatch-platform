package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/** The fuel was delivered (DOM-2.3). */
public record OrderDelivered(UUID eventId, OrderId orderId, Instant occurredAt, OrderStatus status)
        implements DomainEvent {}
