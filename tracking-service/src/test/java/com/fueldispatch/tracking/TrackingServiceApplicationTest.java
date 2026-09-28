package com.fueldispatch.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Smoke test for FND-1.5: the service starts and reports itself healthy. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TrackingServiceApplicationTest {

    @Autowired private WebTestClient webTestClient;

    @Test
    void actuatorHealth_whenApplicationStarts_returnsUp() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
