package com.fueldispatch.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * TRK-NF-1: BlockHound is active in every test JVM of this module (unit and integration), so a
 * blocking call on a non-blocking thread (Netty event loop, Reactor parallel scheduler) fails the
 * test that triggers it. These tests prove the guard is on; they fail if it is ever removed.
 */
class BlockHoundInstalledTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void blockingCallOnNonBlockingThread_isRejected() {
        Mono<Void> sleepsOnParallelThread =
                Mono.fromRunnable(
                                () -> {
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                })
                        .subscribeOn(Schedulers.parallel())
                        .then();

        StepVerifier.create(sleepsOnParallelThread)
                .expectErrorSatisfies(
                        error ->
                                assertThat(error)
                                        .isInstanceOf(BlockingOperationError.class)
                                        .hasMessageContaining("Thread.sleep"))
                .verify(TIMEOUT);
    }

    @Test
    void blockingCallOnBoundedElastic_isAllowed() {
        Mono<String> sleepsOnElastic =
                Mono.fromCallable(
                                () -> {
                                    Thread.sleep(10);
                                    return "done";
                                })
                        .subscribeOn(Schedulers.boundedElastic());

        StepVerifier.create(sleepsOnElastic).expectNext("done").verifyComplete();
    }
}
