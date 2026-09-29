package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened to a dispatch order (DOM-3.2). Sealed so that mappers can switch over
 * every event type exhaustively.
 */
public sealed interface DomainEvent
        permits OrderCreated, OrderApproved, OrderDispatched, OrderDelivered, OrderCancelled {

    UUID eventId();

    OrderId orderId();

    Instant occurredAt();
}
