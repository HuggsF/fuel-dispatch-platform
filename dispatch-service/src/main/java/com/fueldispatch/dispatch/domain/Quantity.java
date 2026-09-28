package com.fueldispatch.dispatch.domain;

import java.math.BigDecimal;

/**
 * Volume of fuel in cubic metres (BR-1): greater than 0 and at most 10,000 m³, with at most three
 * decimal places. Always stored with scale 3, so equal amounts are equal records.
 */
public record Quantity(BigDecimal cubicMetres) {

    public static final int SCALE = 3;
    public static final BigDecimal MAX_CUBIC_METRES = new BigDecimal("10000");

    public Quantity {
        if (cubicMetres == null) {
            throw new DomainValidationException("quantity is required");
        }
        if (cubicMetres.stripTrailingZeros().scale() > SCALE) {
            throw new DomainValidationException(
                    "quantity must have at most " + SCALE + " decimal places");
        }
        if (cubicMetres.signum() <= 0 || cubicMetres.compareTo(MAX_CUBIC_METRES) > 0) {
            throw new DomainValidationException(
                    "quantity must be greater than 0 and at most "
                            + MAX_CUBIC_METRES.toPlainString()
                            + " m³");
        }
        cubicMetres = cubicMetres.setScale(SCALE);
    }
}
