package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;

/** Looks up one order (API-1.3); throws {@code OrderNotFoundException} if absent (API-1.4). */
public interface GetOrderQuery {

    DispatchOrder get(OrderId orderId);
}
