package com.fueldispatch.dispatch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fueldispatch.dispatch.TestcontainersConfiguration;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API-2.4: two transactions that load and approve the same order, as two concurrent requests to the
 * use case would. Interleaved on one thread (REQUIRES_NEW) so the outcome is deterministic.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaOrderRepositoryAdapter.class, OrderJpaMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // each step commits for real
class OptimisticLockingIT {

    private static final Instant T0 = Instant.parse("2026-09-28T10:15:30Z");
    private static final Instant FIRST_WRITER_AT = T0.plusSeconds(60);
    private static final Instant SECOND_WRITER_AT = T0.plusSeconds(120);

    @Autowired private OrderRepository orderRepository;
    @Autowired private SpringDataOrderRepository springDataRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private TransactionTemplate concurrentTransaction;
    private OrderId orderId;

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @BeforeEach
    void storeCreatedOrder() {
        transaction = new TransactionTemplate(transactionManager);
        concurrentTransaction = new TransactionTemplate(transactionManager);
        concurrentTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        DispatchOrder order =
                DispatchOrder.create(
                        new Vessel("Nordic Star", "9321483"),
                        new Berth("B-03"),
                        FuelType.MGO,
                        new Quantity(new BigDecimal("120")),
                        new DeliveryWindow(T0.plusSeconds(86_400), T0.plusSeconds(90_000)),
                        clockAt(T0));
        orderId = order.id();
        transaction.executeWithoutResult(status -> orderRepository.save(order));
    }

    @AfterEach
    void deleteCommittedRows() {
        springDataRepository.deleteAll();
    }

    private void approveInOwnTransaction(TransactionTemplate template, Instant at) {
        template.executeWithoutResult(
                status -> {
                    DispatchOrder order = orderRepository.findById(orderId).orElseThrow();
                    order.approve(clockAt(at));
                    orderRepository.save(order);
                });
    }

    @Test
    void twoConcurrentApprovals_secondToCommitFailsAndFirstWins() {
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            DispatchOrder stale =
                                                    orderRepository.findById(orderId).orElseThrow();

                                            approveInOwnTransaction(
                                                    concurrentTransaction, FIRST_WRITER_AT);

                                            stale.approve(clockAt(SECOND_WRITER_AT));
                                            orderRepository.save(stale);
                                        }))
                .isInstanceOf(OptimisticLockingFailureException.class);

        DispatchOrder stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(stored.updatedAt()).isEqualTo(FIRST_WRITER_AT);
    }

    @Test
    void sequentialTransitions_eachSeesTheLatestVersionAndSucceed() {
        approveInOwnTransaction(transaction, FIRST_WRITER_AT);
        transaction.executeWithoutResult(
                status -> {
                    DispatchOrder order = orderRepository.findById(orderId).orElseThrow();
                    order.dispatch(clockAt(SECOND_WRITER_AT));
                    orderRepository.save(order);
                });

        DispatchOrder stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.status()).isEqualTo(OrderStatus.DISPATCHED);
        assertThat(stored.updatedAt()).isEqualTo(SECOND_WRITER_AT);
    }
}
