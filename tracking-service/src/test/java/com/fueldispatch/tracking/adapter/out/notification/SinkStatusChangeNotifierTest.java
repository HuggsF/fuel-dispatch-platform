package com.fueldispatch.tracking.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fueldispatch.tracking.domain.OrderStatusChanged;
import com.fueldispatch.tracking.domain.OrderSummary;
import com.fueldispatch.tracking.domain.TrackingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Publish/subscribe basics; backpressure and slow subscribers are covered in task 04.6. */
class SinkStatusChangeNotifierTest {

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
                .verify();
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
                .verifyComplete();
    }
}
