package com.fueldispatch.dispatch.domain;

/** Actions an operator can take on an order; each one leads to a single target status. */
public enum OrderAction {
    APPROVE(OrderStatus.APPROVED),
    DISPATCH(OrderStatus.DISPATCHED),
    DELIVER(OrderStatus.DELIVERED),
    CANCEL(OrderStatus.CANCELLED);

    private final OrderStatus targetStatus;

    OrderAction(OrderStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public OrderStatus targetStatus() {
        return targetStatus;
    }
}
