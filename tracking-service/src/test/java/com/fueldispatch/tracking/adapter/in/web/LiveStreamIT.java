package com.fueldispatch.tracking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.tracking.TestcontainersConfiguration;
import com.fueldispatch.tracking.adapter.in.web.dto.StatusChangeEvent;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * TRK-3.1, TRK-3.2 end to end: a client streaming one order receives the change that an event on
 * Kafka applies, through the real listener, service, notifier and SSE endpoint.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, LiveStreamIT.TopicConfig.class})
class LiveStreamIT {

    private static final String TOPIC = "dispatch.orders.v1";
    private static final Path EXAMPLES = Path.of("..", "contracts", "examples");

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfig {

        @Bean
        NewTopic dispatchOrdersTopic() {
            return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired private WebTestClient webTestClient;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void streamOfOneOrder_receivesTheChangeAppliedFromKafka() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String otherOrderId = UUID.randomUUID().toString();
        String created = Files.readString(EXAMPLES.resolve("order-created.json"));

        Flux<ServerSentEvent<StatusChangeEvent>> stream =
                webTestClient
                        .mutate()
                        .responseTimeout(Duration.ofSeconds(30))
                        .build()
                        .get()
                        .uri("/api/v1/tracking/stream?orderId=" + orderId)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .returnResult(
                                new ParameterizedTypeReference<
                                        ServerSentEvent<StatusChangeEvent>>() {})
                        .getResponseBody();

        StepVerifier.create(stream.filter(event -> event.data() != null))
                .then(() -> send(otherOrderId, created))
                .then(() -> send(orderId, created))
                .assertNext(
                        event -> {
                            assertThat(event.event()).isEqualTo("status-changed");
                            assertThat(event.data().orderId()).isEqualTo(UUID.fromString(orderId));
                            assertThat(event.data().status()).isEqualTo(TrackingStatus.CREATED);
                        })
                .thenCancel()
                .verify(Duration.ofSeconds(30));
    }

    private void send(String orderId, String example) {
        String payload =
                example.replaceAll("\"orderId\": \"[^\"]+\"", "\"orderId\": \"" + orderId + "\"")
                        .replaceAll(
                                "\"eventId\": \"[^\"]+\"",
                                "\"eventId\": \"" + UUID.randomUUID() + "\"");
        kafkaTemplate.send(TOPIC, orderId, payload).join();
    }
}
