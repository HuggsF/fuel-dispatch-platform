package com.fueldispatch.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened to a dispatch order (DOM-3.2), with the order data at that moment so that
 * consumers need nothing else. Sealed so that mappers can switch over every event type
 * exhaustively.
 */
public sealed interface DomainEvent
        permits OrderCreated, OrderApproved, OrderDispatched, OrderDelivered, OrderCancelled {

    UUID eventId();

    OrderId orderId();

    Instant occurredAt();

    /** Status of the order right after the change. */
    OrderStatus status();

    Vessel vessel();

    Berth berth();

    FuelType fuelType();

    Quantity quantity();
}
