package com.fueldispatch.tracking.application.port.in;

import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import reactor.core.publisher.Flux;

/** Streams the trackings whose current status is {@code status} (TRK-2.3). */
public interface ListTrackingsQuery {

    Flux<OrderTracking> listByStatus(TrackingStatus status);
}
