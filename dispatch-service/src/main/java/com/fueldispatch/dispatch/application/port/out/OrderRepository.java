package com.fueldispatch.dispatch.application.port.out;

import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import java.util.Optional;

/** Stores and loads dispatch orders. */
public interface OrderRepository {

    /** Inserts or updates the order and returns the persisted state. */
    DispatchOrder save(DispatchOrder order);

    Optional<DispatchOrder> findById(OrderId orderId);

    /**
     * Orders newest first ({@code createdAt} descending); every status when {@code status} is
     * empty.
     */
    OrderPage findPage(Optional<OrderStatus> status, int page, int size);
}
