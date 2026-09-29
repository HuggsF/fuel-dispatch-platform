package com.fueldispatch.dispatch.application;

import com.fueldispatch.dispatch.domain.OrderId;

/** Thrown when a use case targets an order that does not exist (API-1.4). */
public class OrderNotFoundException extends RuntimeException {

    private final OrderId orderId;

    public OrderNotFoundException(OrderId orderId) {
        super("order " + orderId + " not found");
        this.orderId = orderId;
    }

    public OrderId orderId() {
        return orderId;
    }
}
