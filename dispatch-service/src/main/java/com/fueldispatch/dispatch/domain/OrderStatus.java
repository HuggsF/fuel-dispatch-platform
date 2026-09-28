package com.fueldispatch.dispatch.domain;

/**
 * Lifecycle of a dispatch order. {@link #canTransitionTo} is the single source of truth for the
 * transition table in the phase 01 design (BR-4).
 */
public enum OrderStatus {
    CREATED,
    APPROVED,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == APPROVED || target == CANCELLED;
            case APPROVED -> target == DISPATCHED || target == CANCELLED;
            case DISPATCHED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
