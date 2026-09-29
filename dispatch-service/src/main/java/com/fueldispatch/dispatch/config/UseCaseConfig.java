package com.fueldispatch.dispatch.config;

import com.fueldispatch.dispatch.application.port.out.DomainEventPublisher;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.application.service.OrderApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application services to their driven ports; the service stays framework-light. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    OrderApplicationService orderApplicationService(
            OrderRepository orderRepository, DomainEventPublisher eventPublisher, Clock clock) {
        return new OrderApplicationService(orderRepository, eventPublisher, clock);
    }
}
