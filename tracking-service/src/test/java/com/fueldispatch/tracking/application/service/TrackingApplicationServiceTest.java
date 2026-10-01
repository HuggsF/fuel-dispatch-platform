package com.fueldispatch.tracking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fueldispatch.tracking.application.TrackingNotFoundException;
import com.fueldispatch.tracking.application.port.out.ConcurrentTrackingUpdateException;
import com.fueldispatch.tracking.application.port.out.StatusChangeNotifier;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.ApplyResult;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

@ExtendWith(MockitoExtension.class)
class TrackingApplicationServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10");
    private static final OrderSummary SUMMARY =
            new OrderSummary(
                    "MV Atlantic Star", "9321483", "B-03", "VLSFO", new BigDecimal("850.125"));
    private static final Instant T0 = Instant.parse("2026-10-05T13:58:10Z");
    private static final Instant T1 = Instant.parse("2026-10-05T14:03:22Z");

    @Mock private TrackingRepository repository;
    @Mock private StatusChangeNotifier notifier;

    private TrackingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TrackingApplicationService(repository, notifier);
    }

    private static OrderStatusChanged event(UUID orderId, TrackingStatus status, Instant at) {
        return new OrderStatusChanged(UUID.randomUUID(), orderId, at, status, SUMMARY, null);
    }

    private static OrderStatusChanged event(TrackingStatus status, Instant at) {
        return event(ORDER_ID, status, at);
    }

    /** A stored tracking at {@code version} whose latest event is {@code latest}. */
    private static VersionedTracking stored(OrderStatusChanged latest, long version) {
        OrderTracking tracking = OrderTracking.forOrder(latest.orderId());
        tracking.apply(latest);
        return new VersionedTracking(tracking, version);
    }

    private void saveReturnsItsInput() {
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    // TRK-1.1

    @Test
    void apply_firstEventOfOrder_insertsNewTrackingAndNotifies() {
        OrderStatusChanged created = event(TrackingStatus.CREATED, T0);
        when(repository.findById(ORDER_ID)).thenReturn(Mono.empty());
        saveReturnsItsInput();

        StepVerifier.create(service.apply(created))
                .expectNext(ApplyResult.APPLIED)
                .verifyComplete();

        ArgumentCaptor<VersionedTracking> saved = ArgumentCaptor.forClass(VersionedTracking.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().version()).isNull();
        assertThat(saved.getValue().tracking().orderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getValue().tracking().currentStatus()).isEqualTo(TrackingStatus.CREATED);
        verify(notifier).publish(created);
    }

    @Test
    void apply_newerEvent_savesWithLoadedVersionAndNotifies() {
        when(repository.findById(ORDER_ID))
                .thenReturn(Mono.just(stored(event(TrackingStatus.CREATED, T0), 3L)));
        saveReturnsItsInput();
        OrderStatusChanged approved = event(TrackingStatus.APPROVED, T1);

        StepVerifier.create(service.apply(approved))
                .expectNext(ApplyResult.APPLIED)
                .verifyComplete();

        ArgumentCaptor<VersionedTracking> saved = ArgumentCaptor.forClass(VersionedTracking.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().version()).isEqualTo(3L);
        assertThat(saved.getValue().tracking().currentStatus()).isEqualTo(TrackingStatus.APPROVED);
        verify(notifier).publish(approved);
    }

    @Test
    void apply_notifiesOnlyAfterSaveSucceeds() {
        when(repository.findById(ORDER_ID)).thenReturn(Mono.empty());
        TestPublisher<VersionedTracking> save = TestPublisher.create();
        when(repository.save(any())).thenReturn(save.mono());

        StepVerifier.create(service.apply(event(TrackingStatus.CREATED, T0)))
                .then(() -> verify(notifier, never()).publish(any()))
                .then(() -> save.error(new IllegalStateException("MongoDB down")))
                .verifyErrorMessage("MongoDB down");
        verify(notifier, never()).publish(any());
    }

    // TRK-1.2

    @Test
    void apply_duplicateEvent_neitherSavesNorNotifies() {
        OrderStatusChanged created = event(TrackingStatus.CREATED, T0);
        when(repository.findById(ORDER_ID)).thenReturn(Mono.just(stored(created, 1L)));

        StepVerifier.create(service.apply(created))
                .expectNext(ApplyResult.DUPLICATE)
                .verifyComplete();

        verify(repository, never()).save(any());
        verify(notifier, never()).publish(any());
    }

    // TRK-1.3

    @Test
    void apply_olderEvent_savesHistoryButDoesNotNotify() {
        when(repository.findById(ORDER_ID))
                .thenReturn(Mono.just(stored(event(TrackingStatus.APPROVED, T1), 2L)));
        saveReturnsItsInput();

        StepVerifier.create(service.apply(event(TrackingStatus.CREATED, T0)))
                .expectNext(ApplyResult.OUT_OF_ORDER_RECORDED)
                .verifyComplete();

        ArgumentCaptor<VersionedTracking> saved = ArgumentCaptor.forClass(VersionedTracking.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().tracking().history()).hasSize(2);
        assertThat(saved.getValue().tracking().currentStatus()).isEqualTo(TrackingStatus.APPROVED);
        verify(notifier, never()).publish(any());
    }

    // Optimistic locking: retry once (design: "Optimistic locking")

    @Test
    void apply_versionConflictOnce_reloadsRetriesAndNotifiesOnce() {
        OrderStatusChanged approved = event(TrackingStatus.APPROVED, T1);
        when(repository.findById(ORDER_ID))
                .thenReturn(
                        Mono.fromSupplier(() -> stored(event(TrackingStatus.CREATED, T0), 1L)),
                        Mono.fromSupplier(() -> stored(event(TrackingStatus.CREATED, T0), 2L)));
        when(repository.save(any()))
                .thenReturn(Mono.error(new ConcurrentTrackingUpdateException(ORDER_ID, null)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.apply(approved))
                .expectNext(ApplyResult.APPLIED)
                .verifyComplete();

        ArgumentCaptor<VersionedTracking> saved = ArgumentCaptor.forClass(VersionedTracking.class);
        verify(repository, times(2)).findById(ORDER_ID);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(VersionedTracking::version)
                .containsExactly(1L, 2L);
        verify(notifier, times(1)).publish(approved);
    }

    @Test
    void apply_versionConflictTwice_failsWithConflictAndDoesNotNotify() {
        when(repository.findById(ORDER_ID)).thenReturn(Mono.empty());
        when(repository.save(any()))
                .thenReturn(Mono.error(new ConcurrentTrackingUpdateException(ORDER_ID, null)));

        StepVerifier.create(service.apply(event(TrackingStatus.CREATED, T0)))
                .expectError(ConcurrentTrackingUpdateException.class)
                .verify();

        verify(repository, times(2)).save(any());
        verify(notifier, never()).publish(any());
    }

    @Test
    void apply_otherSaveError_isNotRetried() {
        when(repository.findById(ORDER_ID)).thenReturn(Mono.empty());
        when(repository.save(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(service.apply(event(TrackingStatus.CREATED, T0)))
                .expectErrorMessage("boom")
                .verify();

        verify(repository, times(1)).save(any());
    }

    // TRK-2.1, TRK-2.2

    @Test
    void get_existingTracking_returnsIt() {
        VersionedTracking stored = stored(event(TrackingStatus.CREATED, T0), 1L);
        when(repository.findById(ORDER_ID)).thenReturn(Mono.just(stored));

        StepVerifier.create(service.get(ORDER_ID)).expectNext(stored.tracking()).verifyComplete();
    }

    @Test
    void get_unknownOrder_failsWithTrackingNotFound() {
        when(repository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(ORDER_ID))
                .expectErrorSatisfies(
                        error ->
                                assertThat(error)
                                        .asInstanceOf(
                                                InstanceOfAssertFactories.type(
                                                        TrackingNotFoundException.class))
                                        .extracting(TrackingNotFoundException::orderId)
                                        .isEqualTo(ORDER_ID))
                .verify();
    }

    // TRK-2.3

    @Test
    void listByStatus_delegatesToRepository() {
        OrderTracking tracking = stored(event(TrackingStatus.APPROVED, T0), 1L).tracking();
        when(repository.findByStatus(TrackingStatus.APPROVED)).thenReturn(Flux.just(tracking));

        StepVerifier.create(service.listByStatus(TrackingStatus.APPROVED))
                .expectNext(tracking)
                .verifyComplete();
    }

    // TRK-3.1, TRK-3.2

    @Test
    void streamChanges_withoutOrderId_emitsEveryChange() {
        OrderStatusChanged mine = event(TrackingStatus.CREATED, T0);
        OrderStatusChanged other = event(UUID.randomUUID(), TrackingStatus.CREATED, T0);
        when(notifier.changes()).thenReturn(Flux.just(mine, other));

        StepVerifier.create(service.streamChanges(Optional.empty()))
                .expectNext(mine, other)
                .verifyComplete();
    }

    @Test
    void streamChanges_withOrderId_emitsOnlyThatOrder() {
        OrderStatusChanged mine = event(TrackingStatus.CREATED, T0);
        OrderStatusChanged other = event(UUID.randomUUID(), TrackingStatus.CREATED, T0);
        when(notifier.changes()).thenReturn(Flux.just(other, mine));

        StepVerifier.create(service.streamChanges(Optional.of(ORDER_ID)))
                .expectNext(mine)
                .verifyComplete();
    }
}
