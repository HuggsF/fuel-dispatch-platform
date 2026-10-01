package com.fueldispatch.tracking.application;

import java.util.UUID;

/** Thrown when no tracking exists for the requested order (TRK-2.2). */
public class TrackingNotFoundException extends RuntimeException {

    private final UUID orderId;

    public TrackingNotFoundException(UUID orderId) {
        super("tracking of order " + orderId + " not found");
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }
}
