package com.fueldispatch.tracking.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fueldispatch.tracking.application.port.in.ApplyOrderEventUseCase;
import com.fueldispatch.tracking.domain.ApplyResult;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    /** Tests run with the module directory as working directory; contracts live one level up. */
    static final Path EXAMPLES = Path.of("..", "contracts", "examples");

    @Mock private ApplyOrderEventUseCase useCase;
    @Mock private Acknowledgment ack;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        // A bare mapper, not Spring's: the listener must not rely on Boot's Jackson defaults.
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        listener = new OrderEventListener(objectMapper, useCase);
    }

    static String example(String name) throws IOException {
        return Files.readString(EXAMPLES.resolve(name));
    }

    // TRK-1.1, TRK-4.2: the v1 contract maps to the tracking's own event type

    @Test
    void onMessage_createdExample_appliesMappedEvent() throws IOException {
        when(useCase.apply(any())).thenReturn(Mono.just(ApplyResult.APPLIED));

        listener.onMessage(example("order-created.json"), ack);

        ArgumentCaptor<OrderStatusChanged> event =
                ArgumentCaptor.forClass(OrderStatusChanged.class);
        verify(useCase).apply(event.capture());
        assertThat(event.getValue())
                .isEqualTo(
                        new OrderStatusChanged(
                                UUID.fromString("0a6e1d52-8f3b-4c7e-a1d9-2b5c7e8f4a01"),
                                UUID.fromString("1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10"),
                                Instant.parse("2026-10-05T13:58:10.402117Z"),
                                TrackingStatus.CREATED,
                                new OrderSummary(
                                        "MV Atlantic Star",
                                        "9321483",
                                        "B-03",
                                        "VLSFO",
                                        new BigDecimal("850.125")),
                                null));
    }

    @Test
    void onMessage_cancelledExample_keepsReason() throws IOException {
        when(useCase.apply(any())).thenReturn(Mono.just(ApplyResult.APPLIED));

        listener.onMessage(example("order-cancelled.json"), ack);

        ArgumentCaptor<OrderStatusChanged> event =
                ArgumentCaptor.forClass(OrderStatusChanged.class);
        verify(useCase).apply(event.capture());
        assertThat(event.getValue().status()).isEqualTo(TrackingStatus.CANCELLED);
        assertThat(event.getValue().reason())
                .isEqualTo("Vessel departed the berth before the delivery window.");
    }

    @Test
    void onMessage_unknownFields_areIgnored() throws IOException {
        when(useCase.apply(any())).thenReturn(Mono.just(ApplyResult.APPLIED));
        String withExtras =
                example("order-approved.json")
                        .replaceFirst("\\{", "{\"traceId\": \"abc\",")
                        .replace("\"berth\"", "\"pilot\": \"J. Silva\", \"berth\"");

        listener.onMessage(withExtras, ack);

        verify(useCase).apply(any());
        verify(ack).acknowledge();
    }

    // TRK-1.4: acknowledge only once the tracking is saved

    @Test
    void onMessage_useCaseCompletes_acknowledgesAfterIt() throws IOException {
        AtomicBoolean saved = new AtomicBoolean();
        when(useCase.apply(any()))
                .thenReturn(
                        Mono.fromSupplier(
                                () -> {
                                    saved.set(true);
                                    return ApplyResult.APPLIED;
                                }));
        doAnswer(
                        invocation -> {
                            assertThat(saved).isTrue();
                            return null;
                        })
                .when(ack)
                .acknowledge();

        listener.onMessage(example("order-created.json"), ack);

        InOrder order = inOrder(useCase, ack);
        order.verify(useCase).apply(any());
        order.verify(ack).acknowledge();
    }

    @Test
    void onMessage_duplicate_isStillAcknowledged() throws IOException {
        when(useCase.apply(any())).thenReturn(Mono.just(ApplyResult.DUPLICATE));

        listener.onMessage(example("order-created.json"), ack);

        verify(ack).acknowledge();
    }

    @Test
    void onMessage_useCaseFails_propagatesAndDoesNotAcknowledge() throws IOException {
        when(useCase.apply(any()))
                .thenReturn(Mono.error(new IllegalStateException("MongoDB down")));
        String payload = example("order-created.json");

        assertThatThrownBy(() -> listener.onMessage(payload, ack))
                .hasMessageContaining("MongoDB down");
        verify(ack, never()).acknowledge();
    }

    @Test
    void onMessage_malformedJson_failsWithoutCallingUseCaseOrAcknowledging() {
        assertThatThrownBy(() -> listener.onMessage("{not json", ack))
                .isInstanceOf(InvalidOrderEventException.class);
        verify(useCase, never()).apply(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void onMessage_missingRequiredField_failsWithoutAcknowledging() throws IOException {
        String withoutEventId =
                example("order-created.json").replaceFirst("\"eventId\"", "\"ignoredId\"");

        assertThatThrownBy(() -> listener.onMessage(withoutEventId, ack))
                .isInstanceOf(InvalidOrderEventException.class);
        verify(ack, never()).acknowledge();
    }
}
