package com.fueldispatch.tracking.adapter.in.web;

import com.fueldispatch.tracking.adapter.in.web.dto.TrackingResponse;
import com.fueldispatch.tracking.adapter.in.web.dto.TrackingSummaryResponse;
import com.fueldispatch.tracking.application.port.in.GetTrackingQuery;
import com.fueldispatch.tracking.application.port.in.ListTrackingsQuery;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Order tracking over HTTP, non-blocking end to end. Maps to DTOs and delegates. */
@RestController
@RequestMapping(TrackingController.BASE_PATH)
class TrackingController {

    static final String BASE_PATH = "/api/v1/tracking";

    private final GetTrackingQuery getTrackingQuery;
    private final ListTrackingsQuery listTrackingsQuery;

    TrackingController(GetTrackingQuery getTrackingQuery, ListTrackingsQuery listTrackingsQuery) {
        this.getTrackingQuery = getTrackingQuery;
        this.listTrackingsQuery = listTrackingsQuery;
    }

    /** TRK-2.1, TRK-2.2 */
    @GetMapping("/{orderId}")
    Mono<TrackingResponse> get(@PathVariable UUID orderId) {
        return getTrackingQuery.get(orderId).map(TrackingResponse::from);
    }

    /**
     * TRK-2.3: a JSON array for {@code application/json}, one JSON document per line as each one is
     * read for {@code application/x-ndjson}.
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_NDJSON_VALUE})
    Flux<TrackingSummaryResponse> listByStatus(@RequestParam TrackingStatus status) {
        return listTrackingsQuery.listByStatus(status).map(TrackingSummaryResponse::from);
    }
}
