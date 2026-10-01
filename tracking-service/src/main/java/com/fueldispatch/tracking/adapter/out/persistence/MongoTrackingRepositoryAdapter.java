package com.fueldispatch.tracking.adapter.out.persistence;

import com.fueldispatch.tracking.application.port.out.ConcurrentTrackingUpdateException;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.port.out.VersionedTracking;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link TrackingRepository} on MongoDB through reactive Spring Data (TRK-1.1, TRK-2.x). */
@Component
class MongoTrackingRepositoryAdapter implements TrackingRepository {

    private final SpringDataTrackingRepository repository;
    private final OrderTrackingDocumentMapper mapper;

    MongoTrackingRepositoryAdapter(
            SpringDataTrackingRepository repository, OrderTrackingDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<VersionedTracking> findById(UUID orderId) {
        return repository.findById(orderId.toString()).map(mapper::toDomain);
    }

    @Override
    public Flux<OrderTracking> findByStatus(TrackingStatus status) {
        return repository
                .findByCurrentStatus(status)
                .map(mapper::toDomain)
                .map(VersionedTracking::tracking);
    }

    /**
     * Spring Data inserts when the version is {@code null} and otherwise updates with {@code
     * version} in the filter. Its conflict exceptions are translated so the application never sees
     * them.
     */
    @Override
    public Mono<VersionedTracking> save(VersionedTracking tracking) {
        UUID orderId = tracking.tracking().orderId();
        return repository
                .save(mapper.toDocument(tracking))
                .map(mapper::toDomain)
                .onErrorMap(
                        e ->
                                e instanceof OptimisticLockingFailureException
                                        || e instanceof DuplicateKeyException,
                        e -> new ConcurrentTrackingUpdateException(orderId, e));
    }
}
