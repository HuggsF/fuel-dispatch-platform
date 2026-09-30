package com.fueldispatch.dispatch.adapter.out.messaging;

import com.fueldispatch.dispatch.adapter.out.messaging.outbox.OutboxEventJpaEntity;
import com.fueldispatch.dispatch.adapter.out.messaging.outbox.SpringDataOutboxRepository;
import com.fueldispatch.dispatch.application.port.out.DomainEventPublisher;
import com.fueldispatch.dispatch.domain.DomainEvent;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DomainEventPublisher} that writes each event to the outbox table. It joins the caller's
 * transaction and refuses to run without one, so events are stored if and only if the order change
 * commits (EVT-1.1, EVT-1.2). The relay sends them to Kafka later.
 */
@Component
class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final SpringDataOutboxRepository repository;
    private final DispatchOrderEventMapper mapper;

    OutboxDomainEventPublisher(
            SpringDataOutboxRepository repository, DispatchOrderEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(List<DomainEvent> events) {
        repository.saveAll(events.stream().map(this::toRow).toList());
    }

    private OutboxEventJpaEntity toRow(DomainEvent event) {
        return new OutboxEventJpaEntity(
                event.eventId(),
                event.orderId().value(),
                mapper.eventType(event),
                mapper.toJson(event).toString(),
                event.occurredAt());
    }
}
