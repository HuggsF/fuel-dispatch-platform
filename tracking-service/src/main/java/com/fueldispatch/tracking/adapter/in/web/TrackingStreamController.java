package com.fueldispatch.tracking.adapter.in.web;

import com.fueldispatch.tracking.adapter.in.web.dto.StatusChangeEvent;
import com.fueldispatch.tracking.application.port.in.StreamStatusChangesQuery;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Live status changes as Server-Sent Events. The stream never completes; it ends when the client
 * disconnects, which cancels its subscription to the notifier.
 */
@RestController
class TrackingStreamController {

    static final String PATH = TrackingController.BASE_PATH + "/stream";
    static final String EVENT_NAME = "status-changed";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final StreamStatusChangesQuery streamStatusChangesQuery;

    TrackingStreamController(StreamStatusChangesQuery streamStatusChangesQuery) {
        this.streamStatusChangesQuery = streamStatusChangesQuery;
    }

    /**
     * TRK-3.1 every change applied after connecting; TRK-3.2 only {@code orderId}'s when given;
     * TRK-3.3 a {@code :heartbeat} comment every 15 s keeps idle connections (and proxies) alive.
     */
    @GetMapping(path = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<StatusChangeEvent>> stream(
            @RequestParam(required = false) Optional<UUID> orderId) {
        Flux<ServerSentEvent<StatusChangeEvent>> changes =
                streamStatusChangesQuery
                        .streamChanges(orderId)
                        .map(StatusChangeEvent::from)
                        .map(
                                change ->
                                        ServerSentEvent.builder(change)
                                                .event(EVENT_NAME)
                                                .id(change.eventId().toString())
                                                .build());
        // Flux.interval fails when it cannot emit; for a client that stops reading, drop the
        // heartbeat instead of ending the stream (a missed heartbeat is harmless).
        Flux<ServerSentEvent<StatusChangeEvent>> heartbeats =
                Flux.interval(HEARTBEAT_INTERVAL)
                        .onBackpressureDrop()
                        .map(
                                tick ->
                                        ServerSentEvent.<StatusChangeEvent>builder()
                                                .comment("heartbeat")
                                                .build());
        // Prefetch 1: the merge queues at most one change per client, so a slow client gets the
        // change in flight and then the latest one held by the notifier (TRK-3.4).
        return Flux.merge(1, changes, heartbeats);
    }
}
