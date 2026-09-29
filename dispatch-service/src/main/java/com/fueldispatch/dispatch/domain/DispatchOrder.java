package com.fueldispatch.dispatch.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private CancellationReason cancellationReason;
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
            Instant updatedAt,
            CancellationReason cancellationReason) {
        requirePresent(id, "order id");
        requirePresent(vessel, "vessel");
        requirePresent(berth, "berth");
        requirePresent(fuelType, "fuel type");
        requirePresent(quantity, "quantity");
        requirePresent(deliveryWindow, "delivery window");
        requirePresent(status, "status");
        requirePresent(createdAt, "createdAt");
        requirePresent(updatedAt, "updatedAt");
        if (status == OrderStatus.CANCELLED && cancellationReason == null) {
            throw new DomainValidationException(
                    "cancellation reason is required for a CANCELLED order");
        }
        if (status != OrderStatus.CANCELLED && cancellationReason != null) {
            throw new DomainValidationException(
                    "cancellation reason is only allowed for a CANCELLED order");
        }
        this.id = id;
        this.vessel = vessel;
        this.berth = berth;
        this.fuelType = fuelType;
        this.quantity = quantity;
        this.deliveryWindow = deliveryWindow;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancellationReason = cancellationReason;
    }

    /** Registers a new order in status {@code CREATED} (DOM-1.1) and an {@link OrderCreated}. */
    public static DispatchOrder create(
            Vessel vessel,
            Berth berth,
            FuelType fuelType,
            Quantity quantity,
            DeliveryWindow deliveryWindow,
            Clock clock) {
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
                        now,
                        null);
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

    /**
     * Rebuilds a persisted order without registering events. {@code cancellationReason} must be
     * present for a {@code CANCELLED} order and {@code null} otherwise.
     */
    public static DispatchOrder rehydrate(
            OrderId id,
            Vessel vessel,
            Berth berth,
            FuelType fuelType,
            Quantity quantity,
            DeliveryWindow deliveryWindow,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            CancellationReason cancellationReason) {
        return new DispatchOrder(
                id,
                vessel,
                berth,
                fuelType,
                quantity,
                deliveryWindow,
                status,
                createdAt,
                updatedAt,
                cancellationReason);
    }

    /** {@code CREATED -> APPROVED} (DOM-2.1). */
    public void approve(Clock clock) {
        Instant now = transition(OrderAction.APPROVE, clock);
        domainEvents.add(new OrderApproved(UUID.randomUUID(), id, now, status));
    }

    /** {@code APPROVED -> DISPATCHED} (DOM-2.2). */
    public void dispatch(Clock clock) {
        Instant now = transition(OrderAction.DISPATCH, clock);
        domainEvents.add(new OrderDispatched(UUID.randomUUID(), id, now, status));
    }

    /** {@code DISPATCHED -> DELIVERED} (DOM-2.3). */
    public void deliver(Clock clock) {
        Instant now = transition(OrderAction.DELIVER, clock);
        domainEvents.add(new OrderDelivered(UUID.randomUUID(), id, now, status));
    }

    /** {@code CREATED | APPROVED -> CANCELLED}, storing the reason (DOM-2.4). */
    public void cancel(CancellationReason reason, Clock clock) {
        requirePresent(reason, "cancellation reason");
        Instant now = transition(OrderAction.CANCEL, clock);
        cancellationReason = reason;
        domainEvents.add(new OrderCancelled(UUID.randomUUID(), id, now, status, reason));
    }

    /**
     * Checks the transition table (DOM-2.5) before touching any state, then moves to the action's
     * target status and stamps {@code updatedAt} (DOM-2.7).
     */
    private Instant transition(OrderAction action, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (!status.canTransitionTo(action.targetStatus())) {
            throw new InvalidOrderTransitionException(status, action);
        }
        Instant now = clock.instant();
        status = action.targetStatus();
        updatedAt = now;
        return now;
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

    /** Present only once the order has been cancelled. */
    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    /** Read-only snapshot of the events registered and not yet pulled. */
    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    /** Returns the registered events in order and clears them (DOM-3.1). */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pulled = List.copyOf(domainEvents);
        domainEvents.clear();
        return pulled;
    }
}
