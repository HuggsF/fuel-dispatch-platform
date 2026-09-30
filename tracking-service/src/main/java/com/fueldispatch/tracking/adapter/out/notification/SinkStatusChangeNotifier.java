package com.fueldispatch.tracking.adapter.out.notification;

import com.fueldispatch.tracking.application.port.out.StatusChangeNotifier;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory fan-out of applied status changes to the live subscribers of this instance (TRK-3.1).
 * Multi-instance fan-out is future work (design: key decisions).
 */
@Component
class SinkStatusChangeNotifier implements StatusChangeNotifier {

    /**
     * Hot and not replaying: a subscriber sees only changes published after it subscribed. Best
     * effort per subscriber, so one without demand never holds back the others (TRK-3.4).
     */
    private final Sinks.Many<OrderStatusChanged> sink = Sinks.many().multicast().directBestEffort();

    /**
     * Serialized because a sink rejects concurrent emissions ({@code FAIL_NON_SERIALIZED}) and
     * saves complete on several threads. Emission only hands the change to the per-subscriber
     * buffers below, so the lock is held briefly and never waits on a client.
     */
    @Override
    public synchronized void publish(OrderStatusChanged change) {
        sink.tryEmitNext(change);
    }

    /**
     * Each subscriber gets its own {@code onBackpressureLatest()}: it requests everything from the
     * sink and, while its client is slow, keeps only the latest change, dropping the older ones
     * (TRK-3.4).
     */
    @Override
    public Flux<OrderStatusChanged> changes() {
        return sink.asFlux().onBackpressureLatest();
    }
}
