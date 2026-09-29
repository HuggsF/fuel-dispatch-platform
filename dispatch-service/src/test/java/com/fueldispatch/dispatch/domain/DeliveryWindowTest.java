package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** DOM-1.3 / BR-2: the window end must be after its start. */
class DeliveryWindowTest {

    private static final Instant START = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void constructor_endAfterStart_isAccepted() {
        Instant end = START.plusSeconds(3600);

        DeliveryWindow window = new DeliveryWindow(START, end);

        assertThat(window.start()).isEqualTo(START);
        assertThat(window.end()).isEqualTo(end);
    }

    @Test
    void constructor_endOneNanoAfterStart_isAccepted() {
        assertThat(new DeliveryWindow(START, START.plusNanos(1)).end()).isAfter(START);
    }

    @Test
    void constructor_equalInstants_throwsDomainValidationException() {
        assertThatThrownBy(() -> new DeliveryWindow(START, START))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("after");
    }

    @Test
    void constructor_endBeforeStart_throwsDomainValidationException() {
        assertThatThrownBy(() -> new DeliveryWindow(START, START.minusSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void constructor_nullStart_throwsDomainValidationException() {
        assertThatThrownBy(() -> new DeliveryWindow(null, START))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void constructor_nullEnd_throwsDomainValidationException() {
        assertThatThrownBy(() -> new DeliveryWindow(START, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
