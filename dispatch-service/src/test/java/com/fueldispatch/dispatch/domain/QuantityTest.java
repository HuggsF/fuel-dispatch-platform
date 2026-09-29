package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DOM-1.2 / BR-1: 0 < quantity <= 10,000 m³, scale 3. */
class QuantityTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1", "2500.5", "9999.999", "10000", "10000.000"})
    void constructor_withinBounds_isAccepted(String value) {
        Quantity quantity = new Quantity(new BigDecimal(value));

        assertThat(quantity.cubicMetres()).isEqualByComparingTo(value);
    }

    @Test
    void constructor_validValue_isNormalizedToScale3() {
        assertThat(new Quantity(new BigDecimal("12.5")).cubicMetres()).isEqualTo("12.500");
        assertThat(new Quantity(new BigDecimal("1E+1")).cubicMetres()).isEqualTo("10.000");
    }

    @Test
    void constructor_trailingZerosBeyondScale3_isAccepted() {
        assertThat(new Quantity(new BigDecimal("1.50000")).cubicMetres()).isEqualTo("1.500");
    }

    @Test
    void equals_sameAmountDifferentScale_isEqual() {
        assertThat(new Quantity(new BigDecimal("7.5")))
                .isEqualTo(new Quantity(new BigDecimal("7.500")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-0.001", "-1", "10000.001", "10001"})
    void constructor_outOfBounds_throwsDomainValidationException(String value) {
        assertThatThrownBy(() -> new Quantity(new BigDecimal(value)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "1.2345", "10000.0001"})
    void constructor_moreThanThreeDecimals_throwsDomainValidationException(String value) {
        assertThatThrownBy(() -> new Quantity(new BigDecimal(value)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("decimal");
    }

    @Test
    void constructor_null_throwsDomainValidationException() {
        assertThatThrownBy(() -> new Quantity(null)).isInstanceOf(DomainValidationException.class);
    }
}
