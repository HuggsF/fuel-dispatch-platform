package com.fueldispatch.tracking.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderStatusChangedTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-10-05T13:58:10Z");
    private static final OrderSummary SUMMARY =
            new OrderSummary("MV Atlantic Star", "9321483", "B-03", "MGO", BigDecimal.TEN);

    @Test
    void new_missingEventId_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                new OrderStatusChanged(
                                        null, ID, AT, TrackingStatus.CREATED, SUMMARY, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("event id");
    }

    @Test
    void new_missingOrderId_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                new OrderStatusChanged(
                                        ID, null, AT, TrackingStatus.CREATED, SUMMARY, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("order id");
    }

    @Test
    void new_missingOccurredAt_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                new OrderStatusChanged(
                                        ID, ID, null, TrackingStatus.CREATED, SUMMARY, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    void new_missingStatus_throwsDomainValidationException() {
        assertThatThrownBy(() -> new OrderStatusChanged(ID, ID, AT, null, SUMMARY, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void new_missingSummary_throwsDomainValidationException() {
        assertThatThrownBy(
                        () ->
                                new OrderStatusChanged(
                                        ID, ID, AT, TrackingStatus.CREATED, null, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("summary");
    }
}
