package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Published rows older than 7 days are deleted; nothing else is (EVT-NF-1). */
@SpringBootTest(properties = "outbox.relay.enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxCleanupIT {

    private static final Duration A_MINUTE = Duration.ofMinutes(1);
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Autowired private OutboxCleanup cleanup;
    @Autowired private SpringDataOutboxRepository repository;
    @Autowired private Clock clock;

    @BeforeEach
    void emptyOutbox() {
        repository.deleteAll();
    }

    @Test
    void deleteOldPublishedRows_removesOnlyRowsPublishedMoreThanSevenDaysAgo() {
        Instant now = clock.instant();
        save(now.minus(SEVEN_DAYS).minus(A_MINUTE), true);
        UUID publishedRecently = save(now.minus(SEVEN_DAYS).plus(A_MINUTE), true);
        UUID oldButUnpublished = save(now.minus(Duration.ofDays(30)), false);

        int deleted = cleanup.deleteOldPublishedRows();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(OutboxEventJpaEntity::getId)
                .containsExactlyInAnyOrder(publishedRecently, oldButUnpublished);
    }

    @Test
    void deleteOldPublishedRows_rowPublishedTodayOfAnOldEvent_isKept() {
        // Retention counts from publication: an event relayed late (Kafka was down) is kept.
        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity row =
                new OutboxEventJpaEntity(
                        eventId,
                        UUID.randomUUID(),
                        "OrderCreated",
                        "{}",
                        now.minus(Duration.ofDays(10)));
        row.markPublished(now.minus(A_MINUTE));
        repository.save(row);

        assertThat(cleanup.deleteOldPublishedRows()).isZero();
        assertThat(repository.existsById(eventId)).isTrue();
    }

    private UUID save(Instant at, boolean published) {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity row =
                new OutboxEventJpaEntity(eventId, UUID.randomUUID(), "OrderCreated", "{}", at);
        if (published) {
            row.markPublished(at);
        }
        repository.save(row);
        return eventId;
    }
}
