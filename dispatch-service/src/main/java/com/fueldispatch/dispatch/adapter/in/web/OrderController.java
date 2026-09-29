package com.fueldispatch.dispatch.adapter.in.web;

import com.fueldispatch.dispatch.adapter.in.web.dto.CreateOrderRequest;
import com.fueldispatch.dispatch.adapter.in.web.dto.OrderResponse;
import com.fueldispatch.dispatch.adapter.in.web.dto.PageResponse;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.in.GetOrderQuery;
import com.fueldispatch.dispatch.application.port.in.ListOrdersQuery;
import com.fueldispatch.dispatch.application.port.in.OrderPageQuery;
import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orders over HTTP. Maps DTOs to commands and delegates; no business logic (API-NF-1). */
@RestController
@RequestMapping(OrderController.BASE_PATH)
class OrderController {

    static final String BASE_PATH = "/api/v1/orders";

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderQuery getOrderQuery;
    private final ListOrdersQuery listOrdersQuery;

    OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderQuery getOrderQuery,
            ListOrdersQuery listOrdersQuery) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderQuery = getOrderQuery;
        this.listOrdersQuery = listOrdersQuery;
    }

    /** API-1.1 */
    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        DispatchOrder order = createOrderUseCase.create(request.toCommand());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + order.id()))
                .body(OrderResponse.from(order));
    }

    /** API-1.3 */
    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(getOrderQuery.get(new OrderId(id)));
    }

    /** API-1.5 */
    @GetMapping
    PageResponse<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(OrderPageQuery.MAX_SIZE) int size) {
        OrderPage result = listOrdersQuery.list(new OrderPageQuery(status, page, size));
        return PageResponse.of(
                result.items().stream().map(OrderResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements());
    }
}
