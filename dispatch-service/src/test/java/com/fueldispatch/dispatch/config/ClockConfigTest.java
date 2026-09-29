package com.fueldispatch.dispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    private final Clock clock = new ClockConfig().clock();

    @Test
    void clock_isUtc() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    /** PostgreSQL {@code timestamptz} keeps microseconds; finer instants would change on read. */
    @Test
    void clock_ticksInWholeMicroseconds() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Instant now = clock.instant();
            assertThat(now.getNano() % 1_000).as("sub-microsecond part of %s", now).isZero();
            Thread.sleep(0, 37_000);
        }
    }
}
