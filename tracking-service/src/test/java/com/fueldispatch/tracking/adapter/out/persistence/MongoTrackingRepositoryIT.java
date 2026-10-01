package com.fueldispatch.tracking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.tracking.TestcontainersConfiguration;
import com.fueldispatch.tracking.application.port.out.ConcurrentTrackingUpdateException;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.HistoryEntry;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;
import reactor.test.StepVerifier;

/** TRK-1.1, TRK-2.1, TRK-2.3 against a real MongoDB. */
@DataMongoTest
@Import({
    TestcontainersConfiguration.class,
    MongoTrackingRepositoryAdapter.class,
    OrderTrackingDocumentMapper.class
})
class MongoTrackingRepositoryIT {

    private static final OrderSummary SUMMARY =
            new OrderSummary(
                    "MV Atlantic Star", "9321483", "B-03", "VLSFO", new BigDecimal("850.125"));
    private static final Instant T0 = Instant.parse("2026-10-05T13:58:10.402Z");
    private static final Instant T1 = Instant.parse("2026-10-05T14:03:22Z");

    @Autowired private TrackingRepository repository;
    @Autowired private ReactiveMongoTemplate template;

    @BeforeEach
    void cleanCollection() {
        template.remove(new Query(), "order_tracking").block();
    }

    private static OrderTracking trackingWith(UUID orderId, TrackingStatus... statuses) {
        OrderTracking tracking = OrderTracking.forOrder(orderId);
        Instant at = T0;
        for (TrackingStatus status : statuses) {
            String reason = status == TrackingStatus.CANCELLED ? "Vessel left" : null;
            tracking.apply(
                    new OrderStatusChanged(
                            UUID.randomUUID(), orderId, at, status, SUMMARY, reason));
            at = at.plusSeconds(60);
        }
        return tracking;
    }

    private VersionedTracking insert(OrderTracking tracking) {
        return repository.save(new VersionedTracking(tracking, null)).block();
    }

    // TRK-1.1, TRK-2.1
    @Test
    void save_newTracking_findByIdRestoresEveryField() {
        UUID orderId = UUID.randomUUID();
        OrderTracking tracking =
                trackingWith(orderId, TrackingStatus.CREATED, TrackingStatus.CANCELLED);

        insert(tracking);

        StepVerifier.create(repository.findById(orderId))
                .assertNext(
                        loaded -> {
                            OrderTracking t = loaded.tracking();
                            assertThat(loaded.version()).isNotNull();
                            assertThat(t.orderId()).isEqualTo(orderId);
                            assertThat(t.summary()).isEqualTo(SUMMARY);
                            assertThat(t.currentStatus()).isEqualTo(TrackingStatus.CANCELLED);
                            assertThat(t.lastOccurredAt()).isEqualTo(tracking.lastOccurredAt());
                            assertThat(t.history()).containsExactlyElementsOf(tracking.history());
                            assertThat(t.processedEventIds())
                                    .containsExactlyElementsOf(tracking.processedEventIds());
                        })
                .verifyComplete();
    }

    @Test
    void findById_unknownOrder_isEmpty() {
        StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete();
    }

    @Test
    void save_withLoadedVersion_updatesAndReturnsNewVersion() {
        UUID orderId = UUID.randomUUID();
        VersionedTracking saved = insert(trackingWith(orderId, TrackingStatus.CREATED));
        VersionedTracking loaded = repository.findById(orderId).block();
        loaded.tracking()
                .apply(
                        new OrderStatusChanged(
                                UUID.randomUUID(),
                                orderId,
                                T1,
                                TrackingStatus.APPROVED,
                                SUMMARY,
                                null));

        StepVerifier.create(repository.save(loaded))
                .assertNext(updated -> assertThat(updated.version()).isNotEqualTo(saved.version()))
                .verifyComplete();
        StepVerifier.create(repository.findById(orderId))
                .assertNext(
                        reloaded -> {
                            assertThat(reloaded.tracking().currentStatus())
                                    .isEqualTo(TrackingStatus.APPROVED);
                            assertThat(reloaded.tracking().history()).hasSize(2);
                        })
                .verifyComplete();
    }

    // Optimistic locking (design: "Optimistic locking")
    @Test
    void save_staleVersion_failsWithConcurrentTrackingUpdate() {
        UUID orderId = UUID.randomUUID();
        insert(trackingWith(orderId, TrackingStatus.CREATED));
        VersionedTracking first = repository.findById(orderId).block();
        VersionedTracking second = repository.findById(orderId).block();
        repository.save(first).block();

        StepVerifier.create(repository.save(second))
                .expectError(ConcurrentTrackingUpdateException.class)
                .verify();
    }

    @Test
    void save_secondInsertOfSameOrder_failsWithConcurrentTrackingUpdate() {
        UUID orderId = UUID.randomUUID();
        insert(trackingWith(orderId, TrackingStatus.CREATED));

        StepVerifier.create(
                        repository.save(
                                new VersionedTracking(
                                        trackingWith(orderId, TrackingStatus.CREATED), null)))
                .expectError(ConcurrentTrackingUpdateException.class)
                .verify();
    }

    // TRK-2.3
    @Test
    void findByStatus_returnsOnlyTrackingsInThatStatus() {
        UUID approvedA = UUID.randomUUID();
        UUID approvedB = UUID.randomUUID();
        insert(trackingWith(approvedA, TrackingStatus.CREATED, TrackingStatus.APPROVED));
        insert(trackingWith(UUID.randomUUID(), TrackingStatus.CREATED));
        insert(trackingWith(approvedB, TrackingStatus.CREATED, TrackingStatus.APPROVED));

        StepVerifier.create(
                        repository
                                .findByStatus(TrackingStatus.APPROVED)
                                .map(OrderTracking::orderId))
                .recordWith(ArrayList::new)
                .expectNextCount(2)
                .consumeRecordedWith(
                        ids -> assertThat(ids).containsExactlyInAnyOrder(approvedA, approvedB))
                .verifyComplete();
    }

    @Test
    void collection_hasIndexOnCurrentStatus() {
        StepVerifier.create(template.indexOps("order_tracking").getIndexInfo().collectList())
                .assertNext(
                        indexes ->
                                assertThat(indexes)
                                        .flatExtracting(IndexInfo::getIndexFields)
                                        .anySatisfy(
                                                field ->
                                                        assertThat(field.getKey())
                                                                .isEqualTo("currentStatus")))
                .verifyComplete();
    }

    // MongoDB dates have millisecond precision (design: "Domain model" / persistence notes).
    @Test
    void save_subMillisecondInstant_isTruncatedToMilliseconds() {
        UUID orderId = UUID.randomUUID();
        OrderTracking tracking = OrderTracking.forOrder(orderId);
        tracking.apply(
                new OrderStatusChanged(
                        UUID.randomUUID(),
                        orderId,
                        Instant.parse("2026-10-05T13:58:10.402117Z"),
                        TrackingStatus.CREATED,
                        SUMMARY,
                        null));

        insert(tracking);

        StepVerifier.create(repository.findById(orderId))
                .assertNext(
                        loaded -> {
                            assertThat(loaded.tracking().lastOccurredAt())
                                    .isEqualTo(Instant.parse("2026-10-05T13:58:10.402Z"));
                            assertThat(loaded.tracking().history().getFirst().occurredAt())
                                    .isEqualTo(Instant.parse("2026-10-05T13:58:10.402Z"));
                        })
                .verifyComplete();
    }

    @Test
    void history_keepsCancellationReason() {
        UUID orderId = UUID.randomUUID();
        insert(trackingWith(orderId, TrackingStatus.CREATED, TrackingStatus.CANCELLED));

        StepVerifier.create(repository.findById(orderId))
                .assertNext(
                        loaded ->
                                assertThat(loaded.tracking().history())
                                        .extracting(HistoryEntry::reason)
                                        .containsExactly(null, "Vessel left"))
                .verifyComplete();
    }
}
