package com.fueldispatch.dispatch.domain;

/** Thrown when an action is not allowed from the order's current status (DOM-2.5 / BR-4). */
public class InvalidOrderTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final OrderAction action;

    public InvalidOrderTransitionException(OrderStatus currentStatus, OrderAction action) {
        super("cannot " + action + " an order in status " + currentStatus);
        this.currentStatus = currentStatus;
        this.action = action;
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }

    public OrderAction action() {
        return action;
    }
}
