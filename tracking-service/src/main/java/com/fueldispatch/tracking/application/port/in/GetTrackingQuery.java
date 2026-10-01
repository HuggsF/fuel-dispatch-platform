package com.fueldispatch.tracking.application.port.in;

import com.fueldispatch.tracking.domain.OrderTracking;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Looks up the tracking of one order (TRK-2.1); errors with {@code TrackingNotFoundException} if
 * there is none (TRK-2.2).
 */
public interface GetTrackingQuery {

    Mono<OrderTracking> get(UUID orderId);
}
