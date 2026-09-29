package com.fueldispatch.dispatch.domain;

import java.time.Instant;

/** Planned start and end of a delivery (BR-2): the end must be strictly after the start. */
public record DeliveryWindow(Instant start, Instant end) {

    public DeliveryWindow {
        if (start == null || end == null) {
            throw new DomainValidationException("delivery window start and end are required");
        }
        if (!end.isAfter(start)) {
            throw new DomainValidationException("delivery window end must be after its start");
        }
    }
}
