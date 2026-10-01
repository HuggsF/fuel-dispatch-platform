package com.fueldispatch.tracking.config;

import com.fueldispatch.tracking.application.port.out.StatusChangeNotifier;
import com.fueldispatch.tracking.application.port.out.TrackingRepository;
import com.fueldispatch.tracking.application.service.TrackingApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application service to its driven ports; the service stays framework-free. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    TrackingApplicationService trackingApplicationService(
            TrackingRepository trackingRepository, StatusChangeNotifier notifier) {
        return new TrackingApplicationService(trackingRepository, notifier);
    }
}
