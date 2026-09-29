package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.application.port.out.OrderPage;

/** Lists orders newest first, optionally filtered by status (API-1.5). */
public interface ListOrdersQuery {

    OrderPage list(OrderPageQuery query);
}
