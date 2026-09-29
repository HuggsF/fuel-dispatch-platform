package com.fueldispatch.dispatch.adapter.out.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code outbox_event}: one domain event waiting to be relayed to Kafka (EVT-1.1). The id is
 * the event id, assigned by the domain, so {@link Persistable} tells Spring Data to insert new rows
 * instead of looking them up first.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity implements Persistable<UUID> {

    @Id private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Transient private boolean isNew = true;

    protected OutboxEventJpaEntity() {
        // for JPA
    }

    public OutboxEventJpaEntity(
            UUID id, UUID aggregateId, String eventType, String payload, Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    /** Kafka acknowledged the event (EVT-2.3). */
    public void markPublished(Instant when) {
        this.publishedAt = Objects.requireNonNull(when, "when");
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
