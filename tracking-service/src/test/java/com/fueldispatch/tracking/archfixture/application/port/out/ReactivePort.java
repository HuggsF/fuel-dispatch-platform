package com.fueldispatch.tracking.archfixture.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Allowed: a port with Reactor return types. */
public interface ReactivePort {

    Mono<String> findOne();

    Flux<String> findAll();
}
