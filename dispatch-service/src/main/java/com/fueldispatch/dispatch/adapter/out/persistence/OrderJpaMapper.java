package com.fueldispatch.dispatch.adapter.out.persistence;

import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import org.springframework.stereotype.Component;

/** Hand-written mapping between the aggregate and its row (API-3.2). */
@Component
class OrderJpaMapper {

    /** Copies the aggregate's state into {@code entity}, which must have the same id. */
    void copyToEntity(DispatchOrder order, DispatchOrderJpaEntity entity) {
        entity.setVesselName(order.vessel().name());
        entity.setVesselImo(order.vessel().imo());
        entity.setBerth(order.berth().code());
        entity.setFuelType(order.fuelType());
        entity.setQuantityM3(order.quantity().cubicMetres());
        entity.setWindowStart(order.deliveryWindow().start());
        entity.setWindowEnd(order.deliveryWindow().end());
        entity.setStatus(order.status());
        entity.setCancellationReason(
                order.cancellationReason().map(CancellationReason::value).orElse(null));
        entity.setCreatedAt(order.createdAt());
        entity.setUpdatedAt(order.updatedAt());
    }

    /** Rebuilds the aggregate; a row that breaks a domain rule fails here, not later. */
    DispatchOrder toDomain(DispatchOrderJpaEntity entity) {
        return DispatchOrder.rehydrate(
                new OrderId(entity.getId()),
                new Vessel(entity.getVesselName(), entity.getVesselImo()),
                new Berth(entity.getBerth()),
                entity.getFuelType(),
                new Quantity(entity.getQuantityM3()),
                new DeliveryWindow(entity.getWindowStart(), entity.getWindowEnd()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCancellationReason() == null
                        ? null
                        : new CancellationReason(entity.getCancellationReason()));
    }
}
