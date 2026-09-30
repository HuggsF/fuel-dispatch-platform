package com.fueldispatch.tracking.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueldispatch.tracking.application.port.in.ApplyOrderEventUseCase;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import java.time.Duration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code dispatch.orders.v1} in consumer group {@code tracking-service} (TRK-1.4).
 *
 * <p>Blocks on the use case: this runs on a Kafka listener thread, never on a Netty event loop, and
 * blocking is what lets the offset be acknowledged only after the tracking is saved (TRK-1.4). A
 * failure is rethrown without acknowledging, so the container's error handler redelivers the event.
 */
@Component
class OrderEventListener {

    static final String TOPIC = "dispatch.orders.v1";
    static final Duration APPLY_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final ApplyOrderEventUseCase applyOrderEvent;

    OrderEventListener(ObjectMapper objectMapper, ApplyOrderEventUseCase applyOrderEvent) {
        this.objectMapper = objectMapper;
        this.applyOrderEvent = applyOrderEvent;
    }

    @KafkaListener(topics = TOPIC, groupId = "tracking-service")
    void onMessage(String payload, Acknowledgment ack) {
        applyOrderEvent.apply(parse(payload)).block(APPLY_TIMEOUT);
        ack.acknowledge();
    }

    private OrderStatusChanged parse(String payload) {
        try {
            return objectMapper.readValue(payload, OrderEventMessage.class).toDomain();
        } catch (JsonProcessingException | RuntimeException e) {
            throw new InvalidOrderEventException("not a valid v1 order event", e);
        }
    }
}
