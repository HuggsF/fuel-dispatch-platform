package com.fueldispatch.dispatch.adapter.out.messaging.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Locks the next relay batch: up to 100 unpublished rows, oldest first (EVT-2.1). Rows locked
     * by another relay are skipped (EVT-2.5), and a row is taken only when it is the oldest
     * unpublished event of its order, so parallel relays never reorder an order's events (EVT-2.6).
     * The locks last until the caller's transaction ends.
     */
    @Query(
            value =
                    """
                    select o.* from outbox_event o
                    where o.published_at is null
                      and not exists (
                          select 1 from outbox_event earlier
                          where earlier.aggregate_id = o.aggregate_id
                            and earlier.published_at is null
                            and earlier.occurred_at < o.occurred_at)
                    order by o.occurred_at
                    limit 100
                    for update skip locked
                    """,
            nativeQuery = true)
    List<OutboxEventJpaEntity> lockNextBatch();

    long countByPublishedAtIsNull();
}
