package com.fueldispatch.dispatch.adapter.out.messaging;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fueldispatch.dispatch.domain.DomainEvent;
import com.fueldispatch.dispatch.domain.OrderApproved;
import com.fueldispatch.dispatch.domain.OrderCancelled;
import com.fueldispatch.dispatch.domain.OrderCreated;
import com.fueldispatch.dispatch.domain.OrderDelivered;
import com.fueldispatch.dispatch.domain.OrderDispatched;
import org.springframework.stereotype.Component;

/**
 * Maps a domain event to the JSON envelope of {@code contracts/dispatch-order-event.v1.schema.json}
 * (EVT-3.1, EVT-3.2). The tree is built by hand, so the wire format does not depend on how an
 * {@code ObjectMapper} is configured.
 */
@Component
public class DispatchOrderEventMapper {

    /** Topic of this contract version; a breaking change creates {@code dispatch.orders.v2}. */
    public static final String TOPIC = "dispatch.orders.v1";

    static final int EVENT_VERSION = 1;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    public ObjectNode toJson(DomainEvent event) {
        ObjectNode data = NODES.objectNode();
        data.put("status", event.status().name());
        data.put("vesselName", event.vessel().name());
        data.put("vesselImo", event.vessel().imo());
        data.put("berth", event.berth().code());
        data.put("fuelType", event.fuelType().name());
        data.set("quantityM3", NODES.numberNode(event.quantity().cubicMetres()));
        data.put("reason", event instanceof OrderCancelled c ? c.reason().value() : null);

        ObjectNode envelope = NODES.objectNode();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", eventType(event));
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("orderId", event.orderId().value().toString());
        envelope.set("data", data);
        return envelope;
    }

    /** The contract's {@code eventType}; also used for the outbox row and the Kafka header. */
    public String eventType(DomainEvent event) {
        return switch (event) {
            case OrderCreated e -> "OrderCreated";
            case OrderApproved e -> "OrderApproved";
            case OrderDispatched e -> "OrderDispatched";
            case OrderDelivered e -> "OrderDelivered";
            case OrderCancelled e -> "OrderCancelled";
        };
    }
}
