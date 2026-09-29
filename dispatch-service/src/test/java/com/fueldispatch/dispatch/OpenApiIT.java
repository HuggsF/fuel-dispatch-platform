package com.fueldispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** API-4.1: the OpenAPI description and Swagger UI are published. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void apiDocs_describeTheServiceAndEveryOrderEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"title\":\"Dispatch service API\"")
                .contains("\"/api/v1/orders\"")
                .contains("\"/api/v1/orders/{id}\"")
                .contains("\"/api/v1/orders/{id}/approve\"")
                .contains("\"/api/v1/orders/{id}/dispatch\"")
                .contains("\"/api/v1/orders/{id}/deliver\"")
                .contains("\"/api/v1/orders/{id}/cancel\"");
    }

    @Test
    void swaggerUi_isServedFromSwaggerUiHtml() {
        // TestRestTemplate follows the redirect to /swagger-ui/index.html.
        ResponseEntity<String> response =
                restTemplate.getForEntity("/swagger-ui.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
