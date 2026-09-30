package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** What the relay does when Kafka acknowledges or fails (EVT-2.1–2.4, EVT-1.3, EVT-3.3). */
@ExtendWith(MockitoExtension.class)
class OutboxRelayFailureTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:03:23Z");

    @Mock private SpringDataOutboxRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void relayPending_kafkaAcknowledges_sendsKeyedRecordWithHeadersAndMarksRowPublished() {
        OutboxEventJpaEntity row = row("OrderApproved");
        when(repository.lockNextBatch()).thenReturn(List.of(row));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(acknowledged());

        relay.relayPending();

        ProducerRecord<String, String> record = sentRecords(1).getFirst();
        assertThat(record.topic()).isEqualTo("dispatch.orders.v1");
        assertThat(record.key()).isEqualTo(row.getAggregateId().toString());
        assertThat(record.value()).isEqualTo(row.getPayload());
        assertThat(header(record, "eventType")).isEqualTo("OrderApproved");
        assertThat(header(record, "eventId")).isEqualTo(row.getId().toString());
        assertThat(row.getPublishedAt()).isEqualTo(NOW);
    }

    // EVT-2.4: the failed row and every row after it stay for the next poll, so a later event of
    // the same order never overtakes one that failed (EVT-2.2).
    @Test
    void relayPending_sendFails_keepsThatRowAndTheRestUnpublished() {
        OutboxEventJpaEntity first = row("OrderCreated");
        OutboxEventJpaEntity second = row("OrderApproved");
        OutboxEventJpaEntity third = row("OrderDispatched");
        when(repository.lockNextBatch()).thenReturn(List.of(first, second, third));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(acknowledged())
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")));

        relay.relayPending();

        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isNull();
        assertThat(third.getPublishedAt()).isNull();
    }

    // EVT-1.3: Kafka unavailable (send cannot even get metadata) leaves the outbox intact.
    @Test
    void relayPending_kafkaUnavailable_keepsEveryRowUnpublished() {
        OutboxEventJpaEntity first = row("OrderCreated");
        OutboxEventJpaEntity second = row("OrderApproved");
        when(repository.lockNextBatch()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("Topic not present in metadata after 5000 ms"));

        relay.relayPending();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
    }

    // EVT-2.4: the row left behind by a failure goes out on the next poll.
    @Test
    void relayPending_afterAFailedPoll_publishesTheRowOnTheNextPoll() {
        OutboxEventJpaEntity row = row("OrderCreated");
        when(repository.lockNextBatch()).thenReturn(List.of(row));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker down")))
                .thenReturn(acknowledged());

        relay.relayPending();
        assertThat(row.getPublishedAt()).isNull();

        relay.relayPending();
        assertThat(row.getPublishedAt()).isEqualTo(NOW);
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void relayPending_nothingPending_sendsNothing() {
        when(repository.lockNextBatch()).thenReturn(List.of());

        relay.relayPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static OutboxEventJpaEntity row(String eventType) {
        UUID eventId = UUID.randomUUID();
        return new OutboxEventJpaEntity(
                eventId,
                UUID.randomUUID(),
                eventType,
                "{\"eventId\":\"" + eventId + "\"}",
                Instant.parse("2026-10-05T14:03:22Z"));
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private List<ProducerRecord<String, String>> sentRecords(int count) {
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(count)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
