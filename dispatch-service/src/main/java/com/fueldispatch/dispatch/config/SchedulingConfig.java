package com.fueldispatch.dispatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the {@code @Scheduled} jobs: the outbox relay (EVT-2.1). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
