package com.fueldispatch.tracking.adapter.out.persistence;

import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One order per document in {@code order_tracking}; {@code _id} is the order id as a string. Ids
 * are strings so the documents stay readable in any MongoDB client.
 */
@Document("order_tracking")
class OrderTrackingDocument {

    @Id private String id;

    /** Optimistic locking; {@code null} until the first insert (design: "Optimistic locking"). */
    @Version private Long version;

    private Summary summary;

    @Indexed private TrackingStatus currentStatus;

    private Instant lastOccurredAt;
    private List<HistoryItem> history;

    /** Oldest first. */
    private List<String> processedEventIds;

    record Summary(
            String vesselName,
            String vesselImo,
            String berth,
            String fuelType,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal quantityM3) {}

    record HistoryItem(String eventId, TrackingStatus status, Instant occurredAt, String reason) {}

    String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }

    Summary getSummary() {
        return summary;
    }

    void setSummary(Summary summary) {
        this.summary = summary;
    }

    TrackingStatus getCurrentStatus() {
        return currentStatus;
    }

    void setCurrentStatus(TrackingStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    Instant getLastOccurredAt() {
        return lastOccurredAt;
    }

    void setLastOccurredAt(Instant lastOccurredAt) {
        this.lastOccurredAt = lastOccurredAt;
    }

    List<HistoryItem> getHistory() {
        return history;
    }

    void setHistory(List<HistoryItem> history) {
        this.history = history;
    }

    List<String> getProcessedEventIds() {
        return processedEventIds;
    }

    void setProcessedEventIds(List<String> processedEventIds) {
        this.processedEventIds = processedEventIds;
    }
}
