package com.fueldispatch.dispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import java.util.Map;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.ProducerFactory;

/** Producer settings and topic creation against a real Kafka broker (EVT-2.2, EVT-3.3). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaProducerConfigIT {

    @Autowired private ProducerFactory<?, ?> producerFactory;
    @Autowired private KafkaAdmin kafkaAdmin;

    @Test
    void producer_waitsForAllReplicasAndIsIdempotent() {
        Map<String, Object> config = producerFactory.getConfigurationProperties();

        assertThat(config.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(String.valueOf(config.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)))
                .isEqualTo("true");
    }

    // EVT-1.3: with Kafka down, send fails within seconds instead of blocking the relay.
    @Test
    void producer_blocksAtMostFiveSecondsWaitingForKafka() {
        assertThat(
                        String.valueOf(
                                producerFactory
                                        .getConfigurationProperties()
                                        .get(ProducerConfig.MAX_BLOCK_MS_CONFIG)))
                .isEqualTo("5000");
    }

    // EVT-2.2 (key = orderId as text), EVT-3.3 (plain JSON text, no Spring type headers)
    @Test
    void producer_sendsKeyAndValueAsPlainStrings() {
        Map<String, Object> config = producerFactory.getConfigurationProperties();

        assertThat(config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
        assertThat(config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(StringSerializer.class);
    }

    @Test
    void startup_createsTheDispatchOrdersTopicWithThreePartitions() {
        Map<String, TopicDescription> topics = kafkaAdmin.describeTopics("dispatch.orders.v1");

        assertThat(topics.get("dispatch.orders.v1").partitions()).hasSize(3);
    }
}
