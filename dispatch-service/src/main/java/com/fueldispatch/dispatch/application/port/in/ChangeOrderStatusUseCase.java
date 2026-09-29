package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.DispatchOrder;

/** Moves an order through its lifecycle: approve, dispatch, deliver or cancel (API-2.1, 2.2). */
public interface ChangeOrderStatusUseCase {

    DispatchOrder changeStatus(ChangeStatusCommand command);
}
