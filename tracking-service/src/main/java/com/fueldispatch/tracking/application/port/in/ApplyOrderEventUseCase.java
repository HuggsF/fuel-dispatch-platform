package com.fueldispatch.tracking.application.port.in;

import com.fueldispatch.tracking.domain.ApplyResult;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import reactor.core.publisher.Mono;

/**
 * Updates the tracking of the order from one event (TRK-1.1 to TRK-1.3). Completes once the
 * tracking is saved, so the caller may acknowledge the event only then (TRK-1.4).
 */
public interface ApplyOrderEventUseCase {

    Mono<ApplyResult> apply(OrderStatusChanged event);
}
