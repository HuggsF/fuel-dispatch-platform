package com.fueldispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Smoke test for FND-1.5: the service starts and reports itself healthy. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DispatchServiceApplicationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void actuatorHealth_whenApplicationStarts_returnsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
