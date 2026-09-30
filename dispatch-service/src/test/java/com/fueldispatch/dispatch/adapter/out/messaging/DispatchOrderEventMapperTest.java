package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.DomainEvent;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every domain event maps to an envelope that honours the v1 contract (EVT-3.1, EVT-3.2). */
class DispatchOrderEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:03:22.123456Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CancellationReason REASON =
            new CancellationReason("Vessel departed the berth.");

    private static EventContract contract;

    private final DispatchOrderEventMapper mapper = new DispatchOrderEventMapper();

    @BeforeAll
    static void loadContract() {
        contract = EventContract.v1();
    }

    static Stream<Arguments> events() {
        DispatchOrder delivered = newOrder();
        delivered.approve(CLOCK);
        delivered.dispatch(CLOCK);
        delivered.deliver(CLOCK);
        List<DomainEvent> lifecycle = delivered.pullDomainEvents();

        DispatchOrder cancelled = newOrder();
        cancelled.cancel(REASON, CLOCK);
        DomainEvent cancellation = cancelled.pullDomainEvents().getLast();

        return Stream.of(
                Arguments.of("OrderCreated", "CREATED", lifecycle.get(0)),
                Arguments.of("OrderApproved", "APPROVED", lifecycle.get(1)),
                Arguments.of("OrderDispatched", "DISPATCHED", lifecycle.get(2)),
                Arguments.of("OrderDelivered", "DELIVERED", lifecycle.get(3)),
                Arguments.of("OrderCancelled", "CANCELLED", cancellation));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void toJson_eachEventType_honoursTheContract(
            String eventType, String status, DomainEvent event) {
        JsonNode json = mapper.toJson(event);

        assertThat(contract.violations(json)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void toJson_eachEventType_carriesTheEnvelopeAndOrderData(
            String eventType, String status, DomainEvent event) {
        JsonNode json = mapper.toJson(event);

        assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(json.get("eventType").asText()).isEqualTo(eventType);
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-05T14:03:22.123456Z");
        assertThat(json.get("orderId").asText()).isEqualTo(event.orderId().value().toString());

        JsonNode data = json.get("data");
        assertThat(data.get("status").asText()).isEqualTo(status);
        assertThat(data.get("vesselName").asText()).isEqualTo("MV Atlantic Star");
        assertThat(data.get("vesselImo").asText()).isEqualTo("9321483");
        assertThat(data.get("berth").asText()).isEqualTo("B-03");
        assertThat(data.get("fuelType").asText()).isEqualTo("VLSFO");
        assertThat(data.get("quantityM3").decimalValue())
                .isEqualByComparingTo(new BigDecimal("850.5"));
    }

    @Test
    void toJson_cancellation_carriesTheReason() {
        DispatchOrder order = newOrder();
        order.cancel(REASON, CLOCK);

        JsonNode json = mapper.toJson(order.pullDomainEvents().getLast());

        assertThat(json.at("/data/reason").asText()).isEqualTo("Vessel departed the berth.");
    }

    @Test
    void toJson_otherEvents_haveANullReason() {
        JsonNode json = mapper.toJson(newOrder().pullDomainEvents().getFirst());

        assertThat(json.at("/data/reason").isNull()).isTrue();
    }

    @Test
    void toJson_quantity_isAPlainJsonNumberKeepingItsScale() throws Exception {
        String json =
                new ObjectMapper()
                        .writeValueAsString(
                                mapper.toJson(newOrder().pullDomainEvents().getFirst()));

        assertThat(json).contains("\"quantityM3\":850.500");
    }

    private static DispatchOrder newOrder() {
        return DispatchOrder.create(
                new Vessel("MV Atlantic Star", "9321483"),
                new Berth("B-03"),
                FuelType.VLSFO,
                new Quantity(new BigDecimal("850.5")),
                new DeliveryWindow(
                        Instant.parse("2026-10-06T08:00:00Z"),
                        Instant.parse("2026-10-06T14:00:00Z")),
                CLOCK);
    }
}
