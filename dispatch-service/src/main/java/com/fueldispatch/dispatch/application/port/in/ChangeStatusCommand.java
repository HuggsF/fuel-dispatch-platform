package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.OrderId;
import java.util.Objects;

/** Applies {@code action} to an order. {@code reason} is required for, and only for, CANCEL. */
public record ChangeStatusCommand(OrderId orderId, OrderAction action, CancellationReason reason) {

    public ChangeStatusCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(action, "action");
        if (action == OrderAction.CANCEL && reason == null) {
            throw new IllegalArgumentException("reason is required to CANCEL an order");
        }
        if (action != OrderAction.CANCEL && reason != null) {
            throw new IllegalArgumentException("reason is only allowed to CANCEL an order");
        }
    }
}
