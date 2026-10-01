package com.fueldispatch.tracking.domain;

/** Outcome of {@link OrderTracking#apply(OrderStatusChanged)}. */
public enum ApplyResult {
    /** The event is the latest one: status and history were updated (TRK-1.1). */
    APPLIED,
    /** The event id was already processed; nothing changed (TRK-1.2). */
    DUPLICATE,
    /** The event is older than the latest applied one: history only (TRK-1.3). */
    OUT_OF_ORDER_RECORDED
}
