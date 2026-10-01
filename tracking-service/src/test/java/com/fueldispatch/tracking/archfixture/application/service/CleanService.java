package com.fueldispatch.tracking.archfixture.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/** Allowed: {@code @Service}, {@code @Transactional} and Reactor core types. */
@Service
public class CleanService {

    @Transactional(readOnly = true)
    public Mono<String> query() {
        return Mono.just("ok").retryWhen(Retry.max(1));
    }
}
