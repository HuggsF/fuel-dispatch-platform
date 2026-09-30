package com.fueldispatch.tracking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fueldispatch.tracking.adapter.in.web.dto.StatusChangeEvent;
import com.fueldispatch.tracking.application.port.in.StreamStatusChangesQuery;
import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** HTTP contract of the live stream (TRK-3.x); the query is mocked. */
@WebFluxTest(TrackingStreamController.class)
class TrackingStreamControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10");
    private static final UUID EVENT_ID = UUID.fromString("7b1c9e24-5d6a-4f8b-b3c2-1e9d8a7f6c02");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<ServerSentEvent<StatusChangeEvent>> SSE =
            new ParameterizedTypeReference<>() {};

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private StreamStatusChangesQuery streamStatusChangesQuery;

    private static OrderStatusChanged approved() {
        return new OrderStatusChanged(
                EVENT_ID,
                ORDER_ID,
                Instant.parse("2026-10-05T14:03:22Z"),
                TrackingStatus.APPROVED,
                new OrderSummary(
                        "MV Atlantic Star", "9321483", "B-03", "VLSFO", new BigDecimal("850.125")),
                null);
    }

    private Flux<ServerSentEvent<StatusChangeEvent>> open(String uri) {
        return webTestClient
                .get()
                .uri(uri)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(SSE)
                .getResponseBody();
    }

    // TRK-3.1

    @Test
    void stream_pushesEachChangeAsStatusChangedEvent() {
        when(streamStatusChangesQuery.streamChanges(Optional.empty()))
                .thenReturn(Flux.just(approved()).concatWith(Flux.never()));

        StepVerifier.create(open("/api/v1/tracking/stream"))
                .assertNext(
                        event -> {
                            assertThat(event.event()).isEqualTo("status-changed");
                            assertThat(event.id()).isEqualTo(EVENT_ID.toString());
                            assertThat(event.data())
                                    .isEqualTo(
                                            new StatusChangeEvent(
                                                    EVENT_ID,
                                                    ORDER_ID,
                                                    TrackingStatus.APPROVED,
                                                    Instant.parse("2026-10-05T14:03:22Z"),
                                                    null,
                                                    "MV Atlantic Star",
                                                    "9321483",
                                                    "B-03",
                                                    "VLSFO",
                                                    new BigDecimal("850.125")));
                        })
                .thenCancel()
                .verify(TIMEOUT);
    }

    // TRK-3.2

    @Test
    void stream_withOrderId_asksOnlyForThatOrder() {
        when(streamStatusChangesQuery.streamChanges(Optional.of(ORDER_ID)))
                .thenReturn(Flux.just(approved()).concatWith(Flux.never()));

        StepVerifier.create(open("/api/v1/tracking/stream?orderId=" + ORDER_ID))
                .assertNext(event -> assertThat(event.data().orderId()).isEqualTo(ORDER_ID))
                .thenCancel()
                .verify(TIMEOUT);
        verify(streamStatusChangesQuery).streamChanges(Optional.of(ORDER_ID));
    }

    @Test
    void stream_invalidOrderId_returns400ValidationFailed() {
        webTestClient
                .get()
                .uri("/api/v1/tracking/stream?orderId=not-a-uuid")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("orderId");
        verifyNoInteractions(streamStatusChangesQuery);
    }

    // TRK-3.3: virtual time, so the test does not wait 15 real seconds

    @Test
    void stream_idle_sendsHeartbeatCommentEvery15Seconds() {
        when(streamStatusChangesQuery.streamChanges(Optional.empty())).thenReturn(Flux.never());
        TrackingStreamController controller =
                new TrackingStreamController(streamStatusChangesQuery);

        StepVerifier.withVirtualTime(() -> controller.stream(Optional.empty()))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(15).minusMillis(1))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(
                        event -> {
                            assertThat(event.comment()).isEqualTo("heartbeat");
                            assertThat(event.data()).isNull();
                            assertThat(event.event()).isNull();
                        })
                .thenAwait(Duration.ofSeconds(15))
                .assertNext(event -> assertThat(event.comment()).isEqualTo("heartbeat"))
                .thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    void stream_changesAndHeartbeats_areInterleaved() {
        // Built on each call, i.e. inside withVirtualTime, so the delay runs on virtual time.
        when(streamStatusChangesQuery.streamChanges(Optional.empty()))
                .thenAnswer(
                        invocation ->
                                Flux.just(approved())
                                        .delaySubscription(Duration.ofSeconds(20))
                                        .concatWith(Flux.never()));
        TrackingStreamController controller =
                new TrackingStreamController(streamStatusChangesQuery);

        StepVerifier.withVirtualTime(() -> controller.stream(Optional.empty()))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(15))
                .assertNext(event -> assertThat(event.comment()).isEqualTo("heartbeat"))
                .thenAwait(Duration.ofSeconds(5))
                .assertNext(event -> assertThat(event.event()).isEqualTo("status-changed"))
                .thenCancel()
                .verify(TIMEOUT);
    }
}
