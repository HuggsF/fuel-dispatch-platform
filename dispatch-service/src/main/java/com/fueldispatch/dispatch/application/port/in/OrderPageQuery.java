package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.OrderStatus;

/**
 * One page of orders, optionally filtered by {@code status} (null means any status). Page numbers
 * start at 0 and a page holds 1 to {@value #MAX_SIZE} orders (API-1.5).
 */
public record OrderPageQuery(OrderStatus status, int page, int size) {

    public static final int MAX_SIZE = 100;

    public OrderPageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
