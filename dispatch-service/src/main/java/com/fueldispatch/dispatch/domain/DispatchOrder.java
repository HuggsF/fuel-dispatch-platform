package com.fueldispatch.dispatch.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root: a request to deliver fuel to a vessel at a berth. Protects its invariants and
 * registers a domain event for every state change.
 */
public class DispatchOrder {

    private final OrderId id;
    private final Vessel vessel;
    private final Berth berth;
    private final FuelType fuelType;
    private final Quantity quantity;
    private final DeliveryWindow deliveryWindow;
    private final OrderStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private DispatchOrder(
            OrderId id,
            Vessel vessel,
            Berth berth,
            FuelType fuelType,
            Quantity quantity,
            DeliveryWindow deliveryWindow,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.vessel = vessel;
        this.berth = berth;
        this.fuelType = fuelType;
        this.quantity = quantity;
        this.deliveryWindow = deliveryWindow;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Registers a new order in status {@code CREATED} (DOM-1.1) and an {@link OrderCreated}. */
    public static DispatchOrder create(
            Vessel vessel,
            Berth berth,
            FuelType fuelType,
            Quantity quantity,
            DeliveryWindow deliveryWindow,
            Clock clock) {
        requirePresent(vessel, "vessel");
        requirePresent(berth, "berth");
        requirePresent(fuelType, "fuel type");
        requirePresent(quantity, "quantity");
        requirePresent(deliveryWindow, "delivery window");
        Objects.requireNonNull(clock, "clock");

        Instant now = clock.instant();
        DispatchOrder order =
                new DispatchOrder(
                        OrderId.newId(),
                        vessel,
                        berth,
                        fuelType,
                        quantity,
                        deliveryWindow,
                        OrderStatus.CREATED,
                        now,
                        now);
        order.domainEvents.add(
                new OrderCreated(
                        UUID.randomUUID(),
                        order.id,
                        now,
                        order.status,
                        vessel,
                        berth,
                        fuelType,
                        quantity,
                        deliveryWindow));
        return order;
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new DomainValidationException(name + " is required");
        }
    }

    public OrderId id() {
        return id;
    }

    public Vessel vessel() {
        return vessel;
    }

    public Berth berth() {
        return berth;
    }

    public FuelType fuelType() {
        return fuelType;
    }

    public Quantity quantity() {
        return quantity;
    }

    public DeliveryWindow deliveryWindow() {
        return deliveryWindow;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Read-only snapshot of the events registered and not yet pulled. */
    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }
}
