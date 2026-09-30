package com.fueldispatch.dispatch.adapter.out.messaging;

import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the outbox small: once a day, deletes rows published more than the retention period ago (7
 * days by default, EVT-NF-1). Unpublished rows are never deleted. Safe to run on several instances
 * at once: the delete is idempotent.
 */
@Component
class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final SpringDataOutboxRepository repository;
    private final Clock clock;
    private final Duration retention;

    OutboxCleanup(
            SpringDataOutboxRepository repository,
            Clock clock,
            @Value("${outbox.cleanup.retention:P7D}") Duration retention) {
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${outbox.cleanup.cron:0 0 3 * * *}", zone = "UTC")
    @Transactional
    public int deleteOldPublishedRows() {
        int deleted = repository.deletePublishedBefore(clock.instant().minus(retention));
        log.info("Outbox cleanup deleted {} rows published more than {} ago", deleted, retention);
        return deleted;
    }
}
