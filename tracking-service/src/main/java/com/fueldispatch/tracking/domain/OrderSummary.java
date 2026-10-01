package com.fueldispatch.tracking.domain;

import java.math.BigDecimal;

/**
 * Order data carried by every event ({@code data} in the v1 contract, minus status and reason).
 * Kept as plain values: the read side trusts the contract validated by the producer.
 */
public record OrderSummary(
        String vesselName,
        String vesselImo,
        String berth,
        String fuelType,
        BigDecimal quantityM3) {}
