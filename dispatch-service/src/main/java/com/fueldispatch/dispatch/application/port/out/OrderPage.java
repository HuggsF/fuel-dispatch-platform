package com.fueldispatch.dispatch.application.port.out;

import com.fueldispatch.dispatch.domain.DispatchOrder;
import java.util.List;

/** A page of orders, framework-free so no Spring Data type leaks out of persistence. */
public record OrderPage(List<DispatchOrder> items, int page, int size, long totalElements) {

    public OrderPage {
        items = List.copyOf(items);
    }
}
