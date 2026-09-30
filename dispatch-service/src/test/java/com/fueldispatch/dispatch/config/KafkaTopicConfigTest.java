package com.fueldispatch.dispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigTest {

    @Test
    void dispatchOrdersTopic_isVersionedWithTheConfiguredPartitionsAndReplicas() {
        NewTopic topic = new KafkaTopicConfig().dispatchOrdersTopic(3, (short) 1);

        assertThat(topic.name()).isEqualTo("dispatch.orders.v1");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
