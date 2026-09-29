package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/** The order was approved (DOM-2.1). */
public record OrderApproved(UUID eventId, OrderId orderId, Instant occurredAt, OrderStatus status)
        implements DomainEvent {}
