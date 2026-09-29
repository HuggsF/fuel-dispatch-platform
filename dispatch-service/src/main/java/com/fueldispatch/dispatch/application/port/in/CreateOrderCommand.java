package com.fueldispatch.dispatch.application.port.in;

import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;

/** Data for a new order. Missing components are rejected by {@code DispatchOrder.create}. */
public record CreateOrderCommand(
        Vessel vessel,
        Berth berth,
        FuelType fuelType,
        Quantity quantity,
        DeliveryWindow deliveryWindow) {}
