package com.fueldispatch.dispatch.adapter.out.persistence;

import com.fueldispatch.dispatch.domain.OrderStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrderRepository extends JpaRepository<DispatchOrderJpaEntity, UUID> {

    Page<DispatchOrderJpaEntity> findByStatus(OrderStatus status, Pageable pageable);
}
