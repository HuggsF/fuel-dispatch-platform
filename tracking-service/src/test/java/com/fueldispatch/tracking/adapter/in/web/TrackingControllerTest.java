package com.fueldispatch.tracking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fueldispatch.tracking.adapter.in.web.dto.TrackingSummaryResponse;
import com.fueldispatch.tracking.application.TrackingNotFoundException;
import com.fueldispatch.tracking.application.port.in.GetTrackingQuery;
import com.fueldispatch.tracking.application.port.in.ListTrackingsQuery;
import com.fueldispatch.tracking.domain.HistoryEntry;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.OrderTracking;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** HTTP contract of the tracking queries (TRK-2.x); use cases are mocked. */
@WebFluxTest(TrackingController.class)
class TrackingControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("1f0e4c8a-3b7d-4e2a-9c61-5d8f2a7b9e10");
    private static final UUID CREATED_EVENT =
            UUID.fromString("0a6e1d52-8f3b-4c7e-a1d9-2b5c7e8f4a01");
    private static final UUID APPROVED_EVENT =
            UUID.fromString("7b1c9e24-5d6a-4f8b-b3c2-1e9d8a7f6c02");
    private static final Instant T0 = Instant.parse("2026-10-05T13:58:10.402Z");
    private static final Instant T1 = Instant.parse("2026-10-05T14:03:22Z");
    private static final OrderSummary SUMMARY =
            new OrderSummary(
                    "MV Atlantic Star", "9321483", "B-03", "VLSFO", new BigDecimal("850.125"));

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private GetTrackingQuery getTrackingQuery;
    @MockitoBean private ListTrackingsQuery listTrackingsQuery;

    private static OrderTracking approvedTracking(UUID orderId) {
        return OrderTracking.rehydrate(
                orderId,
                SUMMARY,
                TrackingStatus.APPROVED,
                T1,
                List.of(
                        new HistoryEntry(CREATED_EVENT, TrackingStatus.CREATED, T0, null),
                        new HistoryEntry(APPROVED_EVENT, TrackingStatus.APPROVED, T1, null)),
                List.of(CREATED_EVENT, APPROVED_EVENT));
    }

    // TRK-2.1

    @Test
    void get_existingTracking_returnsStatusSummaryAndSortedHistory() {
        when(getTrackingQuery.get(ORDER_ID)).thenReturn(Mono.just(approvedTracking(ORDER_ID)));

        webTestClient
                .get()
                .uri("/api/v1/tracking/{orderId}", ORDER_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo(ORDER_ID.toString())
                .jsonPath("$.currentStatus")
                .isEqualTo("APPROVED")
                .jsonPath("$.lastOccurredAt")
                .isEqualTo("2026-10-05T14:03:22Z")
                .jsonPath("$.vesselName")
                .isEqualTo("MV Atlantic Star")
                .jsonPath("$.vesselImo")
                .isEqualTo("9321483")
                .jsonPath("$.berth")
                .isEqualTo("B-03")
                .jsonPath("$.fuelType")
                .isEqualTo("VLSFO")
                .jsonPath("$.quantityM3")
                .isEqualTo(850.125)
                .jsonPath("$.history.length()")
                .isEqualTo(2)
                .jsonPath("$.history[0].eventId")
                .isEqualTo(CREATED_EVENT.toString())
                .jsonPath("$.history[0].status")
                .isEqualTo("CREATED")
                .jsonPath("$.history[0].occurredAt")
                .isEqualTo("2026-10-05T13:58:10.402Z")
                .jsonPath("$.history[0].reason")
                .isEmpty()
                .jsonPath("$.history[1].status")
                .isEqualTo("APPROVED")
                .jsonPath("$.processedEventIds")
                .doesNotExist();
    }

    // TRK-2.2

    @Test
    void get_unknownOrder_returns404TrackingNotFound() {
        when(getTrackingQuery.get(ORDER_ID))
                .thenReturn(Mono.error(new TrackingNotFoundException(ORDER_ID)));

        webTestClient
                .get()
                .uri("/api/v1/tracking/{orderId}", ORDER_ID)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.title")
                .isEqualTo("Tracking not found")
                .jsonPath("$.code")
                .isEqualTo("TRACKING_NOT_FOUND")
                .jsonPath("$.detail")
                .value(detail -> assertThat((String) detail).contains(ORDER_ID.toString()));
    }

    @Test
    void get_invalidOrderId_returns400ValidationFailed() {
        webTestClient
                .get()
                .uri("/api/v1/tracking/not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("orderId");
        verifyNoInteractions(getTrackingQuery);
    }

    // TRK-2.3

    @Test
    void list_byStatusAsJson_returnsArrayOfSummaries() {
        UUID other = UUID.randomUUID();
        when(listTrackingsQuery.listByStatus(TrackingStatus.APPROVED))
                .thenReturn(Flux.just(approvedTracking(ORDER_ID), approvedTracking(other)));

        webTestClient
                .get()
                .uri("/api/v1/tracking?status=APPROVED")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(2)
                .jsonPath("$[0].orderId")
                .isEqualTo(ORDER_ID.toString())
                .jsonPath("$[0].currentStatus")
                .isEqualTo("APPROVED")
                .jsonPath("$[0].vesselName")
                .isEqualTo("MV Atlantic Star")
                .jsonPath("$[0].history")
                .doesNotExist()
                .jsonPath("$[1].orderId")
                .isEqualTo(other.toString());
    }

    @Test
    void list_byStatusAsNdjson_streamsOneSummaryPerLine() {
        UUID other = UUID.randomUUID();
        when(listTrackingsQuery.listByStatus(TrackingStatus.APPROVED))
                .thenReturn(Flux.just(approvedTracking(ORDER_ID), approvedTracking(other)));

        Flux<TrackingSummaryResponse> body =
                webTestClient
                        .get()
                        .uri("/api/v1/tracking?status=APPROVED")
                        .accept(MediaType.APPLICATION_NDJSON)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                        .returnResult(TrackingSummaryResponse.class)
                        .getResponseBody();

        StepVerifier.create(body.map(TrackingSummaryResponse::orderId))
                .expectNext(ORDER_ID, other)
                .verifyComplete();
    }

    @Test
    void list_noMatches_returnsEmptyArray() {
        when(listTrackingsQuery.listByStatus(TrackingStatus.DELIVERED)).thenReturn(Flux.empty());

        webTestClient
                .get()
                .uri("/api/v1/tracking?status=DELIVERED")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
    }

    @Test
    void list_unknownStatus_returns400ValidationFailed() {
        webTestClient
                .get()
                .uri("/api/v1/tracking?status=LOST")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("status");
        verifyNoInteractions(listTrackingsQuery);
    }

    @Test
    void list_missingStatus_returns400ValidationFailed() {
        webTestClient
                .get()
                .uri("/api/v1/tracking")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("status")
                .jsonPath("$.errors[0].message")
                .isEqualTo("is required");
    }

    // Error handling (tech.md: RFC 9457 with a code)

    @Test
    void get_unexpectedError_returns500WithoutLeakingTheCause() {
        when(getTrackingQuery.get(any()))
                .thenReturn(Mono.error(new IllegalStateException("secret connection string")));

        webTestClient
                .get()
                .uri("/api/v1/tracking/{orderId}", ORDER_ID)
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail")
                .value(detail -> assertThat((String) detail).doesNotContain("secret"));
    }

    @Test
    void unknownRoute_returns404WithStatusNameAsCode() {
        webTestClient
                .get()
                .uri("/api/v1/nothing-here")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND");
    }
}
