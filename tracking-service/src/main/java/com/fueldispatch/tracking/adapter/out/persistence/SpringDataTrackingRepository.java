package com.fueldispatch.tracking.adapter.out.persistence;

import com.fueldispatch.tracking.domain.TrackingStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

interface SpringDataTrackingRepository
        extends ReactiveMongoRepository<OrderTrackingDocument, String> {

    Flux<OrderTrackingDocument> findByCurrentStatus(TrackingStatus currentStatus);
}
