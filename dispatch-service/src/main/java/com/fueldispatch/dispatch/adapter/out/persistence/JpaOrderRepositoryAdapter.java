package com.fueldispatch.dispatch.adapter.out.persistence;

import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** {@link OrderRepository} on PostgreSQL through Spring Data JPA (API-3.1, API-3.2). */
@Component
class JpaOrderRepositoryAdapter implements OrderRepository {

    /** Newest first; the id breaks ties so pages stay stable (API-1.5). */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SpringDataOrderRepository repository;
    private final OrderJpaMapper mapper;

    JpaOrderRepositoryAdapter(SpringDataOrderRepository repository, OrderJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Updates the row already managed in the current transaction, or inserts a new one, so the use
     * case's load and save touch the same entity.
     */
    @Override
    public DispatchOrder save(DispatchOrder order) {
        DispatchOrderJpaEntity entity =
                repository
                        .findById(order.id().value())
                        .orElseGet(() -> new DispatchOrderJpaEntity(order.id().value()));
        mapper.copyToEntity(order, entity);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<DispatchOrder> findById(OrderId orderId) {
        return repository.findById(orderId.value()).map(mapper::toDomain);
    }

    @Override
    public OrderPage findPage(Optional<OrderStatus> status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, NEWEST_FIRST);
        Page<DispatchOrderJpaEntity> result =
                status.map(s -> repository.findByStatus(s, pageRequest))
                        .orElseGet(() -> repository.findAll(pageRequest));
        return new OrderPage(
                result.map(mapper::toDomain).getContent(), page, size, result.getTotalElements());
    }
}
