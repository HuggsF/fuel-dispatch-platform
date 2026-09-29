package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.DispatchOrder;

/** Registers a new dispatch order (API-1.1). */
public interface CreateOrderUseCase {

    DispatchOrder create(CreateOrderCommand command);
}
