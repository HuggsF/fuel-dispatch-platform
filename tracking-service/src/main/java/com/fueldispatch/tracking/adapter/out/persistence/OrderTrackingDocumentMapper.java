package com.fueldispatch.tracking.adapter.out.persistence;

import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.HistoryEntry;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.OrderTracking;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Hand-written mapping between the aggregate and its document. */
@Component
class OrderTrackingDocumentMapper {

    OrderTrackingDocument toDocument(VersionedTracking versioned) {
        OrderTracking tracking = versioned.tracking();
        OrderSummary summary = tracking.summary();

        OrderTrackingDocument document = new OrderTrackingDocument();
        document.setId(tracking.orderId().toString());
        document.setVersion(versioned.version());
        document.setSummary(
                summary == null
                        ? null
                        : new OrderTrackingDocument.Summary(
                                summary.vesselName(),
                                summary.vesselImo(),
                                summary.berth(),
                                summary.fuelType(),
                                summary.quantityM3()));
        document.setCurrentStatus(tracking.currentStatus());
        document.setLastOccurredAt(tracking.lastOccurredAt());
        document.setHistory(
                tracking.history().stream()
                        .map(
                                entry ->
                                        new OrderTrackingDocument.HistoryItem(
                                                entry.eventId().toString(),
                                                entry.status(),
                                                entry.occurredAt(),
                                                entry.reason()))
                        .toList());
        document.setProcessedEventIds(
                tracking.processedEventIds().stream().map(UUID::toString).toList());
        return document;
    }

    VersionedTracking toDomain(OrderTrackingDocument document) {
        OrderTrackingDocument.Summary summary = document.getSummary();
        OrderTracking tracking =
                OrderTracking.rehydrate(
                        UUID.fromString(document.getId()),
                        summary == null
                                ? null
                                : new OrderSummary(
                                        summary.vesselName(),
                                        summary.vesselImo(),
                                        summary.berth(),
                                        summary.fuelType(),
                                        summary.quantityM3()),
                        document.getCurrentStatus(),
                        document.getLastOccurredAt(),
                        document.getHistory().stream()
                                .map(
                                        item ->
                                                new HistoryEntry(
                                                        UUID.fromString(item.eventId()),
                                                        item.status(),
                                                        item.occurredAt(),
                                                        item.reason()))
                                .toList(),
                        document.getProcessedEventIds().stream().map(UUID::fromString).toList());
        return new VersionedTracking(tracking, document.getVersion());
    }
}
