package com.fueldispatch.dispatch.adapter.out.messaging;

import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and publishes pending events to Kafka, oldest first (EVT-2.1). Each message is
 * keyed by order id (EVT-2.2) and carries {@code eventType} and {@code eventId} headers (EVT-3.3).
 * A row is marked published only once Kafka acknowledges it (EVT-2.3); on the first failure the
 * batch stops and that row and the rest wait for the next poll (EVT-2.4), so a later event of an
 * order never overtakes an earlier one. Delivery is at-least-once: consumers dedupe by eventId.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final SpringDataOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    OutboxRelay(
            SpringDataOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    /** One batch per run, in its own transaction; published_at is written on commit. */
    @Scheduled(fixedDelayString = "${outbox.relay.interval:1000}")
    @Transactional
    public void relayPending() {
        for (OutboxEventJpaEntity row :
                repository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            if (!send(row)) {
                return;
            }
            row.markPublished(clock.instant());
        }
    }

    private boolean send(OutboxEventJpaEntity row) {
        try {
            kafkaTemplate.send(toRecord(row)).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // Any send failure (broker down, timeout, rejected record) leaves the row for retry.
            log.warn(
                    "Outbox relay could not publish event {} of order {}; retrying on next poll",
                    row.getId(),
                    row.getAggregateId(),
                    e);
            return false;
        }
    }

    private static ProducerRecord<String, String> toRecord(OutboxEventJpaEntity row) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        DispatchOrderEventMapper.TOPIC,
                        row.getAggregateId().toString(),
                        row.getPayload());
        record.headers().add("eventType", row.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", row.getId().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
