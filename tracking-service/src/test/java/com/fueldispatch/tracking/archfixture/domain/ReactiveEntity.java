package com.fueldispatch.tracking.archfixture.domain;

import reactor.core.publisher.Mono;

/** Violation: Reactor is allowed in the application layer, not in the domain. */
public class ReactiveEntity {

    public Mono<String> name() {
        return Mono.just("reactive");
    }
}
