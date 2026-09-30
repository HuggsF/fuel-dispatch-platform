package com.fueldispatch.tracking.application.service;

import com.fueldispatch.tracking.application.TrackingNotFoundException;
import com.fueldispatch.tracking.application.port.in.ApplyOrderEventUseCase;
import com.fueldispatch.tracking.application.port.in.GetTrackingQuery;
import com.fueldispatch.tracking.application.port.in.ListTrackingsQuery;
import com.fueldispatch.tracking.application.port.in.StreamStatusChangesQuery;
import com.fueldispatch.tracking.application.port.out.ConcurrentTrackingUpdateException;
import com.fueldispatch.tracking.application.port.out.StatusChangeNotifier;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.ApplyResult;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Implements the tracking use cases. No transactions: each apply is one document write guarded by
 * optimistic locking. Registered as a bean by a {@code config} class once its adapters exist.
 */
public class TrackingApplicationService
        implements ApplyOrderEventUseCase,
                GetTrackingQuery,
                ListTrackingsQuery,
                StreamStatusChangesQuery {

    /** A concurrent write is retried once on a fresh read; a second conflict is an error. */
    private static final Retry RETRY_ONCE_ON_CONFLICT =
            Retry.max(1)
                    .filter(ConcurrentTrackingUpdateException.class::isInstance)
                    .onRetryExhaustedThrow((spec, signal) -> signal.failure());

    private final TrackingRepository repository;
    private final StatusChangeNotifier notifier;

    public TrackingApplicationService(
            TrackingRepository repository, StatusChangeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Loads or creates the tracking, applies the event and saves it unless it is a duplicate
     * (TRK-1.1 to TRK-1.3). Live subscribers hear only of {@link ApplyResult#APPLIED} changes, and
     * only after the save.
     */
    @Override
    public Mono<ApplyResult> apply(OrderStatusChanged event) {
        return Mono.defer(() -> applyOnce(event))
                .retryWhen(RETRY_ONCE_ON_CONFLICT)
                .doOnNext(
                        result -> {
                            if (result == ApplyResult.APPLIED) {
                                notifier.publish(event);
                            }
                        });
    }

    private Mono<ApplyResult> applyOnce(OrderStatusChanged event) {
        return repository
                .findById(event.orderId())
                .switchIfEmpty(
                        Mono.fromSupplier(
                                () ->
                                        new VersionedTracking(
                                                OrderTracking.forOrder(event.orderId()), null)))
                .flatMap(
                        versioned -> {
                            ApplyResult result = versioned.tracking().apply(event);
                            if (result == ApplyResult.DUPLICATE) {
                                return Mono.just(result);
                            }
                            return repository.save(versioned).thenReturn(result);
                        });
    }

    @Override
    public Mono<OrderTracking> get(UUID orderId) {
        return repository
                .findById(orderId)
                .map(VersionedTracking::tracking)
                .switchIfEmpty(Mono.error(() -> new TrackingNotFoundException(orderId)));
    }

    @Override
    public Flux<OrderTracking> listByStatus(TrackingStatus status) {
        return repository.findByStatus(status);
    }

    @Override
    public Flux<OrderStatusChanged> streamChanges(Optional<UUID> orderId) {
        return orderId.map(id -> notifier.changes().filter(change -> change.orderId().equals(id)))
                .orElseGet(notifier::changes);
    }
}
