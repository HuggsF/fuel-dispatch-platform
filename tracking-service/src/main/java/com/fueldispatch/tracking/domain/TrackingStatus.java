package com.fueldispatch.tracking.domain;

/**
 * Order status as seen by the read side. Mirrors the {@code data.status} values of the v1 event
 * contract; it is owned by tracking-service and not shared with dispatch-service (TRK-4.2).
 */
public enum TrackingStatus {
    CREATED,
    APPROVED,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
