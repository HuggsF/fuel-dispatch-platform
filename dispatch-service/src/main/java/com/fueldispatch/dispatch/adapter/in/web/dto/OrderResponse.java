package com.fueldispatch.dispatch.adapter.in.web.dto;

import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An order as returned by the API; {@code cancellationReason} is null unless cancelled. */
public record OrderResponse(
        UUID id,
        String vesselName,
        String vesselImo,
        String berth,
        FuelType fuelType,
        BigDecimal quantityM3,
        Instant windowStart,
        Instant windowEnd,
        OrderStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(DispatchOrder order) {
        return new OrderResponse(
                order.id().value(),
                order.vessel().name(),
                order.vessel().imo(),
                order.berth().code(),
                order.fuelType(),
                order.quantity().cubicMetres(),
                order.deliveryWindow().start(),
                order.deliveryWindow().end(),
                order.status(),
                order.cancellationReason().map(CancellationReason::value).orElse(null),
                order.createdAt(),
                order.updatedAt());
    }
}
