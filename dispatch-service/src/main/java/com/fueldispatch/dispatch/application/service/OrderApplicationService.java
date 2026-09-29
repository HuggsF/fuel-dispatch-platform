package com.fueldispatch.dispatch.application.service;

import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.application.port.in.ChangeOrderStatusUseCase;
import com.fueldispatch.dispatch.application.port.in.ChangeStatusCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.in.GetOrderQuery;
import com.fueldispatch.dispatch.application.port.in.ListOrdersQuery;
import com.fueldispatch.dispatch.application.port.in.OrderPageQuery;
import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the order use cases; each public method is one transaction (API-3.3). Registered as a
 * bean by {@code UseCaseConfig} once the persistence adapter exists.
 */
public class OrderApplicationService
        implements CreateOrderUseCase, ChangeOrderStatusUseCase, GetOrderQuery, ListOrdersQuery {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderApplicationService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public DispatchOrder create(CreateOrderCommand command) {
        DispatchOrder order =
                DispatchOrder.create(
                        command.vessel(),
                        command.berth(),
                        command.fuelType(),
                        command.quantity(),
                        command.deliveryWindow(),
                        clock);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public DispatchOrder changeStatus(ChangeStatusCommand command) {
        DispatchOrder order = load(command.orderId());
        switch (command.action()) {
            case APPROVE -> order.approve(clock);
            case DISPATCH -> order.dispatch(clock);
            case DELIVER -> order.deliver(clock);
            case CANCEL -> order.cancel(command.reason(), clock);
        }
        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchOrder get(OrderId orderId) {
        return load(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPage list(OrderPageQuery query) {
        return orderRepository.findPage(
                Optional.ofNullable(query.status()), query.page(), query.size());
    }

    private DispatchOrder load(OrderId orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
