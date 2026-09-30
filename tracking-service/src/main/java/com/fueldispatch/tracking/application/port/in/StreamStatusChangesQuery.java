package com.fueldispatch.tracking.application.port.in;

import com.fueldispatch.tracking.domain.OrderStatusChanged;
import java.util.Optional;
import java.util.UUID;
import reactor.core.publisher.Flux;

/**
 * Live status changes applied after subscription (TRK-3.1), only those of {@code orderId} when
 * present (TRK-3.2). Never completes on its own.
 */
public interface StreamStatusChangesQuery {

    Flux<OrderStatusChanged> streamChanges(Optional<UUID> orderId);
}
