package com.fueldispatch.dispatch.config;

import com.fueldispatch.dispatch.adapter.out.messaging.DispatchOrderEventMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the order events topic; Spring's {@code KafkaAdmin} creates it on startup if missing.
 * Partitions spread orders across consumers while the key (orderId) keeps each order's events in
 * sequence (EVT-2.2). Replication is 1 on the single local broker.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicConfig {

    @Bean
    NewTopic dispatchOrdersTopic(
            @Value("${dispatch.kafka.topic.partitions:3}") int partitions,
            @Value("${dispatch.kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(DispatchOrderEventMapper.TOPIC)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
