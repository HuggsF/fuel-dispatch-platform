package com.fueldispatch.dispatch.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The v1 event contract and its examples (EVT-3.1, EVT-3.2). */
class DispatchOrderEventContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static EventContract contract;

    @BeforeAll
    static void loadContract() {
        contract = EventContract.v1();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "order-created.json",
                "order-approved.json",
                "order-dispatched.json",
                "order-delivered.json",
                "order-cancelled.json"
            })
    void example_ofEachEventType_honoursTheContract(String file) throws IOException {
        assertThat(contract.violations(example(file))).isEmpty();
    }

    @Test
    void examplesDirectory_holdsOneExamplePerEventType() throws IOException {
        try (Stream<Path> files = Files.list(EventContract.EXAMPLES_DIR)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .containsExactlyInAnyOrder(
                            "order-created.json",
                            "order-approved.json",
                            "order-dispatched.json",
                            "order-delivered.json",
                            "order-cancelled.json");
        }
    }

    @Test
    void event_withUnknownFields_stillHonoursTheContract() throws IOException {
        // Additive, optional fields keep v1, so consumers must tolerate them.
        ObjectNode event = example("order-approved.json");
        event.put("traceId", "abc");
        ((ObjectNode) event.get("data")).put("terminal", "T1");

        assertThat(contract.violations(event)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void invalidEvent_breaksTheContract(
            String description, String file, Consumer<ObjectNode> breakIt) throws IOException {
        ObjectNode event = example(file);
        breakIt.accept(event);

        assertThat(contract.violations(event)).as(description).isNotEmpty();
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                invalid("missing eventId", "order-approved.json", e -> e.remove("eventId")),
                invalid("missing eventType", "order-approved.json", e -> e.remove("eventType")),
                invalid(
                        "missing eventVersion",
                        "order-approved.json",
                        e -> e.remove("eventVersion")),
                invalid("missing occurredAt", "order-approved.json", e -> e.remove("occurredAt")),
                invalid("missing orderId", "order-approved.json", e -> e.remove("orderId")),
                invalid("missing data", "order-approved.json", e -> e.remove("data")),
                invalid(
                        "eventId is not a UUID",
                        "order-approved.json",
                        e -> e.put("eventId", "not-a-uuid")),
                invalid(
                        "orderId is not a UUID",
                        "order-approved.json",
                        e -> e.put("orderId", "42")),
                invalid(
                        "unknown eventType",
                        "order-approved.json",
                        e -> e.put("eventType", "OrderShipped")),
                invalid("eventVersion is 2", "order-approved.json", e -> e.put("eventVersion", 2)),
                invalid(
                        "occurredAt is not a date-time",
                        "order-approved.json",
                        e -> e.put("occurredAt", "yesterday")),
                invalid(
                        "occurredAt is not UTC",
                        "order-approved.json",
                        e -> e.put("occurredAt", "2026-10-05T11:03:22-03:00")),
                invalid(
                        "status does not match eventType",
                        "order-approved.json",
                        e -> data(e).put("status", "DISPATCHED")),
                invalid(
                        "missing vesselName",
                        "order-approved.json",
                        e -> data(e).remove("vesselName")),
                invalid(
                        "blank vesselName",
                        "order-approved.json",
                        e -> data(e).put("vesselName", "  ")),
                invalid(
                        "IMO with 6 digits",
                        "order-approved.json",
                        e -> data(e).put("vesselImo", "932148")),
                invalid("missing berth", "order-approved.json", e -> data(e).remove("berth")),
                invalid(
                        "unknown fuelType",
                        "order-approved.json",
                        e -> data(e).put("fuelType", "LNG")),
                invalid(
                        "quantity is zero",
                        "order-approved.json",
                        e -> data(e).put("quantityM3", 0)),
                invalid(
                        "quantity above 10,000",
                        "order-approved.json",
                        e -> data(e).put("quantityM3", 10000.001)),
                invalid(
                        "quantity with four decimals",
                        "order-approved.json",
                        e -> data(e).put("quantityM3", 850.1234)),
                invalid(
                        "reason on a non-cancellation",
                        "order-approved.json",
                        e -> data(e).put("reason", "why not")),
                invalid(
                        "cancellation without reason",
                        "order-cancelled.json",
                        e -> data(e).remove("reason")),
                invalid(
                        "cancellation with null reason",
                        "order-cancelled.json",
                        e -> data(e).putNull("reason")),
                invalid(
                        "cancellation with blank reason",
                        "order-cancelled.json",
                        e -> data(e).put("reason", "   ")),
                invalid(
                        "cancellation reason over 500 characters",
                        "order-cancelled.json",
                        e -> data(e).put("reason", "x".repeat(501))));
    }

    private static Arguments invalid(
            String description, String file, Consumer<ObjectNode> breakIt) {
        return Arguments.of(description, file, breakIt);
    }

    private static ObjectNode data(ObjectNode event) {
        return (ObjectNode) event.get("data");
    }

    private static ObjectNode example(String file) throws IOException {
        JsonNode node = JSON.readTree(EventContract.EXAMPLES_DIR.resolve(file).toFile());
        return (ObjectNode) node;
    }
}
