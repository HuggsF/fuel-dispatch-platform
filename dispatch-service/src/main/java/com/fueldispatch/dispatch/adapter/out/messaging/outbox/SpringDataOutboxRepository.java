package com.fueldispatch.dispatch.adapter.out.messaging.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /** The next relay batch: up to 100 unpublished rows, oldest first (EVT-2.1). */
    List<OutboxEventJpaEntity> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
