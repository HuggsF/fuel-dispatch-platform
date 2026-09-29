package com.fueldispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** End to end over HTTP against real PostgreSQL: the whole order lifecycle. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

    private static final String CREATE_BODY =
            """
            {"vesselName": "Nordic Star", "vesselImo": "9321483", "berth": "b-03",
             "fuelType": "VLSFO", "quantityM3": 850.5,
             "windowStart": "2026-10-01T08:00:00Z", "windowEnd": "2026-10-01T14:00:00Z"}
            """;

    @LocalServerPort private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client =
                RestClient.builder()
                        .baseUrl("http://localhost:" + port)
                        // Assert on error statuses instead of throwing.
                        .defaultStatusHandler(status -> true, (request, response) -> {})
                        .build();
    }

    private ResponseEntity<JsonNode> postAction(String id, String action) {
        return client.post()
                .uri("/api/v1/orders/{id}/{action}", id, action)
                .retrieve()
                .toEntity(JsonNode.class);
    }

    @Test
    void createApproveDispatchDeliver_movesTheOrderThroughItsLifecycle() {
        ResponseEntity<JsonNode> created =
                client.post()
                        .uri("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(CREATE_BODY)
                        .retrieve()
                        .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").asText();
        assertThat(created.getHeaders().getLocation()).hasToString("/api/v1/orders/" + id);
        assertThat(created.getBody().get("status").asText()).isEqualTo("CREATED");
        assertThat(created.getBody().get("berth").asText()).isEqualTo("B-03");
        assertThat(created.getBody().get("quantityM3").decimalValue())
                .isEqualByComparingTo("850.5");

        for (String[] step :
                new String[][] {
                    {"approve", "APPROVED"}, {"dispatch", "DISPATCHED"}, {"deliver", "DELIVERED"}
                }) {
            ResponseEntity<JsonNode> response = postAction(id, step[0]);
            assertThat(response.getStatusCode()).as(step[0]).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status").asText()).isEqualTo(step[1]);
        }

        ResponseEntity<JsonNode> stored =
                client.get().uri("/api/v1/orders/{id}", id).retrieve().toEntity(JsonNode.class);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stored.getBody().get("status").asText()).isEqualTo("DELIVERED");
        assertThat(stored.getBody().get("vesselName").asText()).isEqualTo("Nordic Star");

        ResponseEntity<JsonNode> rejected = postAction(id, "approve");
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(rejected.getBody().get("currentStatus").asText()).isEqualTo("DELIVERED");
    }

    @Test
    void cancel_createdOrder_storesTheReason() {
        String id =
                client.post()
                        .uri("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(CREATE_BODY)
                        .retrieve()
                        .body(JsonNode.class)
                        .get("id")
                        .asText();

        ResponseEntity<JsonNode> cancelled =
                client.post()
                        .uri("/api/v1/orders/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"reason\": \"  Vessel delayed  \"}")
                        .retrieve()
                        .toEntity(JsonNode.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("cancellationReason").asText())
                .isEqualTo("Vessel delayed");
    }

    @Test
    void getUnknownOrder_returns404OrderNotFound() {
        ResponseEntity<JsonNode> response =
                client.get()
                        .uri("/api/v1/orders/{id}", "00000000-0000-0000-0000-000000000000")
                        .retrieve()
                        .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ORDER_NOT_FOUND");
    }
}
