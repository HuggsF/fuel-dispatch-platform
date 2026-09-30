package com.fueldispatch.tracking.config;

import com.fueldispatch.tracking.adapter.in.messaging.InvalidOrderEventException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What the listener container does when applying an event fails (TRK-1.4). Spring Boot hands this
 * bean to the auto-configured container factory.
 *
 * <p>Spring Kafka's default retries 10 times without pause and then skips the record, so a MongoDB
 * outage of a few seconds would lose events for good. Here a transient failure is retried until it
 * succeeds: the offset does not move and the partition waits, keeping per-order ordering. Only an
 * invalid payload, which no retry can fix, is logged and skipped (phase 05 adds a dead-letter
 * topic).
 */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    static final long INITIAL_INTERVAL_MS = 1_000;
    static final double MULTIPLIER = 2.0;
    static final long MAX_INTERVAL_MS = 30_000;

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        // No attempt or elapsed-time limit: an event is skipped only if it can never succeed.
        backOff.setMaxElapsedTime(Long.MAX_VALUE);

        DefaultErrorHandler handler = new DefaultErrorHandler(backOff);
        handler.addNotRetryableExceptions(InvalidOrderEventException.class);
        return handler;
    }
}
