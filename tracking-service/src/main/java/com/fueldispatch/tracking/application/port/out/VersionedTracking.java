package com.fueldispatch.tracking.application.port.out;

import com.fueldispatch.tracking.domain.OrderTracking;
import java.util.Objects;

/**
 * A tracking together with the storage version it was read at, so that {@link
 * TrackingRepository#save} can detect a concurrent write. The version is opaque to the application:
 * {@code null} means never saved, anything else is only passed back.
 */
public record VersionedTracking(OrderTracking tracking, Long version) {

    public VersionedTracking {
        Objects.requireNonNull(tracking, "tracking");
    }
}
