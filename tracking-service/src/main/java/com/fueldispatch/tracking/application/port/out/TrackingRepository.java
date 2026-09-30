package com.fueldispatch.tracking.application.port.out;

import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Storage of order trackings (TRK-1.1, TRK-2.1, TRK-2.3). */
public interface TrackingRepository {

    /** The tracking of the order with its current version, or empty when none exists. */
    Mono<VersionedTracking> findById(UUID orderId);

    /** Every tracking whose current status is {@code status}. */
    Flux<OrderTracking> findByStatus(TrackingStatus status);

    /**
     * Inserts when the version is {@code null}, otherwise updates only if the stored version still
     * matches; returns the tracking with its new version. Errors with {@link
     * ConcurrentTrackingUpdateException} when another write got there first.
     */
    Mono<VersionedTracking> save(VersionedTracking tracking);
}
