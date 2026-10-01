package com.fueldispatch.tracking.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class SinkStatusChangeNotifierTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final SinkStatusChangeNotifier notifier = new SinkStatusChangeNotifier();

    private static OrderStatusChanged change() {
        return new OrderStatusChanged(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-10-05T13:58:10Z"),
                TrackingStatus.CREATED,
                new OrderSummary("MV Atlantic Star", "9321483", "B-03", "MGO", BigDecimal.TEN),
                null);
    }

    // TRK-3.1

    @Test
    void changes_receiveOnlyWhatIsPublishedAfterSubscribing() {
        OrderStatusChanged before = change();
        OrderStatusChanged after = change();
        notifier.publish(before);

        StepVerifier.create(notifier.changes())
                .then(() -> notifier.publish(after))
                .expectNext(after)
                .thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    void publish_withoutSubscribers_doesNotFail() {
        assertThatCode(() -> notifier.publish(change())).doesNotThrowAnyException();
    }

    @Test
    void changes_everySubscriberReceivesEachChange() {
        OrderStatusChanged change = change();

        StepVerifier.create(notifier.changes().take(1).mergeWith(notifier.changes().take(1)))
                .then(() -> notifier.publish(change))
                .expectNext(change, change)
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void changes_subscriberThatCancelled_doesNotAffectOthers() {
        OrderStatusChanged first = change();
        OrderStatusChanged second = change();
        List<OrderStatusChanged> received = new CopyOnWriteArrayList<>();
        Disposable staying = notifier.changes().subscribe(received::add);

        StepVerifier.create(notifier.changes())
                .then(() -> notifier.publish(first))
                .expectNext(first)
                .thenCancel()
                .verify(TIMEOUT);
        notifier.publish(second);

        assertThat(received).containsExactly(first, second);
        staying.dispose();
    }

    // TRK-3.4

    @Test
    void slowSubscriber_doesNotBlockFastOneAndGetsOnlyTheLatestChange() {
        OrderStatusChanged first = change();
        OrderStatusChanged second = change();
        OrderStatusChanged third = change();
        List<OrderStatusChanged> fast = new CopyOnWriteArrayList<>();
        Disposable fastSubscriber = notifier.changes().subscribe(fast::add);

        // The slow subscriber requests nothing until all three changes are published.
        StepVerifier.create(notifier.changes(), 0)
                .then(() -> notifier.publish(first))
                .then(() -> notifier.publish(second))
                .then(() -> notifier.publish(third))
                .then(() -> assertThat(fast).containsExactly(first, second, third))
                .thenRequest(1)
                .expectNext(third)
                .thenRequest(1)
                .expectNoEvent(Duration.ofMillis(50))
                .thenCancel()
                .verify(TIMEOUT);

        fastSubscriber.dispose();
    }

    @Test
    void publish_fromConcurrentThreads_reachesSubscriberWithoutLoss() throws Exception {
        int threads = 4;
        int perThread = 500;
        List<OrderStatusChanged> received = new CopyOnWriteArrayList<>();
        Disposable subscriber = notifier.changes().subscribe(received::add);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(
                    () -> {
                        for (int i = 0; i < perThread; i++) {
                            notifier.publish(change());
                        }
                    });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(received).hasSize(threads * perThread);
        subscriber.dispose();
    }
}
