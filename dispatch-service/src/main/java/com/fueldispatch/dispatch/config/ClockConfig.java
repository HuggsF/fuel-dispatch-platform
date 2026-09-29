package com.fueldispatch.dispatch.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The single time source of the service; tests replace it with {@code Clock.fixed}. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /**
     * UTC, ticking in whole microseconds: PostgreSQL {@code timestamptz} keeps microseconds and
     * rounds anything finer, so a finer clock would return timestamps that change on the next read.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
