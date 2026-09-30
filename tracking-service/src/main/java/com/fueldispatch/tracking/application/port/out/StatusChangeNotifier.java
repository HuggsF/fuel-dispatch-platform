package com.fueldispatch.tracking.application.port.out;

import com.fueldispatch.tracking.domain.OrderStatusChanged;
import reactor.core.publisher.Flux;

/** Fans applied status changes out to live subscribers (TRK-3.1). */
public interface StatusChangeNotifier {

    /** Hands a change to current subscribers; never blocks and never fails the caller. */
    void publish(OrderStatusChanged change);

    /** Changes published after subscription. */
    Flux<OrderStatusChanged> changes();
}
