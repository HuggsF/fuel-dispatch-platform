package com.fueldispatch.tracking.adapter.out.notification;

import com.fueldispatch.tracking.application.port.out.StatusChangeNotifier;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory fan-out of applied status changes to live subscribers of this instance (TRK-3.1). Basic
 * publish/subscribe; the backpressure strategy for slow subscribers (TRK-3.4) is task 04.6.
 */
@Component
class SinkStatusChangeNotifier implements StatusChangeNotifier {

    /** Hot: a subscriber sees only changes published after it subscribed; nothing is replayed. */
    private final Sinks.Many<OrderStatusChanged> sink = Sinks.many().multicast().directBestEffort();

    /** Best effort: with no subscribers, or a failed emission, the change is simply not pushed. */
    @Override
    public void publish(OrderStatusChanged change) {
        sink.tryEmitNext(change);
    }

    @Override
    public Flux<OrderStatusChanged> changes() {
        return sink.asFlux();
    }
}
