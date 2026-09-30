package com.fueldispatch.dispatch.application.port.out;

import com.fueldispatch.dispatch.domain.DomainEvent;
import java.util.List;

/**
 * Hands the events of a state change over for delivery to other services. Called by the use case
 * after saving the order, inside the same transaction (EVT-1.1): if the transaction rolls back, the
 * events are discarded with it (EVT-1.2).
 */
public interface DomainEventPublisher {

    void publish(List<DomainEvent> events);
}
