package com.fueldispatch.tracking.application.port.out;

import java.util.UUID;

/**
 * Another write to the same tracking happened between its read and this save: a stale version, or a
 * second insert of the same order.
 */
public class ConcurrentTrackingUpdateException extends RuntimeException {

    public ConcurrentTrackingUpdateException(UUID orderId, Throwable cause) {
        super("tracking of order " + orderId + " was modified concurrently", cause);
    }
}
