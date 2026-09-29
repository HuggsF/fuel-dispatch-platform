package com.fueldispatch.dispatch.adapter.out.messaging.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {}
