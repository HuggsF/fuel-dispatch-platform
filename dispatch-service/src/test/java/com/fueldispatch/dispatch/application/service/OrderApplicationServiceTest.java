package com.fueldispatch.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.application.port.in.ChangeStatusCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.OrderPageQuery;
import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.application.port.out.OrderRepository;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.InvalidOrderTransitionException;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:15:30Z");
    private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Vessel VESSEL = new Vessel("Nordic Star", "9321483");
    private static final Berth BERTH = new Berth("B-03");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("850.5"));
    private static final DeliveryWindow WINDOW =
            new DeliveryWindow(
                    Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-01T14:00:00Z"));
    private static final CancellationReason REASON = new CancellationReason("Vessel delayed");

    @Mock private OrderRepository orderRepository;

    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OrderApplicationService(orderRepository, CLOCK);
    }

    private static DispatchOrder persistedOrder(OrderStatus status) {
        return DispatchOrder.rehydrate(
                OrderId.newId(),
                VESSEL,
                BERTH,
                FuelType.MGO,
                QUANTITY,
                WINDOW,
                status,
                CREATED_AT,
                CREATED_AT,
                status == OrderStatus.CANCELLED ? REASON : null);
    }

    private void saveReturnsItsArgument() {
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // API-1.1
    @Test
    void create_validCommand_savesNewOrderOnceAndReturnsSavedOrder() {
        DispatchOrder saved = persistedOrder(OrderStatus.CREATED);
        when(orderRepository.save(any())).thenReturn(saved);

        DispatchOrder result =
                service.create(
                        new CreateOrderCommand(VESSEL, BERTH, FuelType.VLSFO, QUANTITY, WINDOW));

        ArgumentCaptor<DispatchOrder> captor = ArgumentCaptor.forClass(DispatchOrder.class);
        verify(orderRepository).save(captor.capture());
        DispatchOrder created = captor.getValue();
        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.vessel()).isEqualTo(VESSEL);
        assertThat(created.berth()).isEqualTo(BERTH);
        assertThat(created.fuelType()).isEqualTo(FuelType.VLSFO);
        assertThat(created.quantity()).isEqualTo(QUANTITY);
        assertThat(created.deliveryWindow()).isEqualTo(WINDOW);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(result).isSameAs(saved);
    }

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.CREATED, OrderAction.APPROVE, null),
                Arguments.of(OrderStatus.APPROVED, OrderAction.DISPATCH, null),
                Arguments.of(OrderStatus.DISPATCHED, OrderAction.DELIVER, null),
                Arguments.of(OrderStatus.CREATED, OrderAction.CANCEL, REASON),
                Arguments.of(OrderStatus.APPROVED, OrderAction.CANCEL, REASON));
    }

    // API-2.1, API-2.2
    @ParameterizedTest(name = "{0} --{1}--> saved")
    @MethodSource("allowedTransitions")
    void changeStatus_allowedTransition_loadsTransitionsAndSaves(
            OrderStatus from, OrderAction action, CancellationReason reason) {
        DispatchOrder order = persistedOrder(from);
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));
        saveReturnsItsArgument();

        DispatchOrder result =
                service.changeStatus(new ChangeStatusCommand(order.id(), action, reason));

        verify(orderRepository).save(order);
        assertThat(result).isSameAs(order);
        assertThat(result.status()).isEqualTo(action.targetStatus());
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.cancellationReason()).isEqualTo(Optional.ofNullable(reason));
    }

    // API-1.4
    @Test
    void changeStatus_unknownId_throwsOrderNotFoundAndSavesNothing() {
        OrderId id = OrderId.newId();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        new ChangeStatusCommand(id, OrderAction.APPROVE, null)))
                .isInstanceOf(OrderNotFoundException.class)
                .extracting(e -> ((OrderNotFoundException) e).orderId())
                .isEqualTo(id);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void changeStatus_transitionNotAllowed_propagatesAndSavesNothing() {
        DispatchOrder order = persistedOrder(OrderStatus.CREATED);
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        new ChangeStatusCommand(
                                                order.id(), OrderAction.DISPATCH, null)))
                .isInstanceOf(InvalidOrderTransitionException.class);
        verify(orderRepository, never()).save(any());
    }

    // API-1.3
    @Test
    void get_existingOrder_returnsIt() {
        DispatchOrder order = persistedOrder(OrderStatus.APPROVED);
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        assertThat(service.get(order.id())).isSameAs(order);
    }

    // API-1.4
    @Test
    void get_unknownId_throwsOrderNotFound() {
        OrderId id = OrderId.newId();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void list_withStatus_delegatesFilterAndPagingToRepository() {
        OrderPage page = new OrderPage(List.of(persistedOrder(OrderStatus.APPROVED)), 2, 10, 21);
        when(orderRepository.findPage(Optional.of(OrderStatus.APPROVED), 2, 10)).thenReturn(page);

        assertThat(service.list(new OrderPageQuery(OrderStatus.APPROVED, 2, 10))).isSameAs(page);
    }

    @Test
    void list_withoutStatus_asksRepositoryForAllStatuses() {
        OrderPage page = new OrderPage(List.of(), 0, 20, 0);
        when(orderRepository.findPage(Optional.empty(), 0, 20)).thenReturn(page);

        assertThat(service.list(new OrderPageQuery(null, 0, 20))).isSameAs(page);
    }

    // API-3.3
    @Test
    void useCases_runInOneTransaction_queriesReadOnly() throws NoSuchMethodException {
        assertTransactional("create", false, CreateOrderCommand.class);
        assertTransactional("changeStatus", false, ChangeStatusCommand.class);
        assertTransactional("get", true, OrderId.class);
        assertTransactional("list", true, OrderPageQuery.class);
    }

    private static void assertTransactional(String name, boolean readOnly, Class<?> parameter)
            throws NoSuchMethodException {
        Method method = OrderApplicationService.class.getMethod(name, parameter);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).as("@Transactional on %s", name).isNotNull();
        assertThat(transactional.readOnly()).as("readOnly on %s", name).isEqualTo(readOnly);
    }
}
