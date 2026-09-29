package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Fuel types from the glossary in specs/steering/product.md. */
class FuelTypeTest {

    @Test
    void values_matchGlossary() {
        assertThat(FuelType.values()).containsExactly(FuelType.MGO, FuelType.VLSFO, FuelType.HFO);
    }
}
