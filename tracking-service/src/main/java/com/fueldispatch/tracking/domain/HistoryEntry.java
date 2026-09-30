package com.fueldispatch.tracking.domain;

import java.time.Instant;
import java.util.UUID;

/** One status change in the history of an order. {@code reason} is set only for cancellations. */
public record HistoryEntry(
        UUID eventId, TrackingStatus status, Instant occurredAt, String reason) {}
