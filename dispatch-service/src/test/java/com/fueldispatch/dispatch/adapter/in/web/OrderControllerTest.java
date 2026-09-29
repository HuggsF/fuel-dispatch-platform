package com.fueldispatch.dispatch.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.in.GetOrderQuery;
import com.fueldispatch.dispatch.application.port.in.ListOrdersQuery;
import com.fueldispatch.dispatch.application.port.in.OrderPageQuery;
import com.fueldispatch.dispatch.application.port.out.OrderPage;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.DispatchOrder;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HTTP contract of create / read / list (API-1.x, API-NF-1); use cases are mocked. */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:15:30Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-28T11:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-10-01T14:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CreateOrderUseCase createOrderUseCase;
    @MockitoBean private GetOrderQuery getOrderQuery;
    @MockitoBean private ListOrdersQuery listOrdersQuery;

    private static DispatchOrder order(OrderStatus status) {
        return DispatchOrder.rehydrate(
                OrderId.newId(),
                new Vessel("Nordic Star", "9321483"),
                new Berth("B-03"),
                FuelType.VLSFO,
                new Quantity(new BigDecimal("850.5")),
                new DeliveryWindow(WINDOW_START, WINDOW_END),
                status,
                CREATED_AT,
                UPDATED_AT,
                status == OrderStatus.CANCELLED ? new CancellationReason("Vessel delayed") : null);
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vesselName", "Nordic Star");
        body.put("vesselImo", "9321483");
        body.put("berth", "B-03");
        body.put("fuelType", "VLSFO");
        body.put("quantityM3", new BigDecimal("850.5"));
        body.put("windowStart", WINDOW_START.toString());
        body.put("windowEnd", WINDOW_END.toString());
        return body;
    }

    private ResultActions postOrder(Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    private static void assertOrderJson(ResultActions result, DispatchOrder order)
            throws Exception {
        result.andExpect(jsonPath("$.id").value(order.id().toString()))
                .andExpect(jsonPath("$.vesselName").value("Nordic Star"))
                .andExpect(jsonPath("$.vesselImo").value("9321483"))
                .andExpect(jsonPath("$.berth").value("B-03"))
                .andExpect(jsonPath("$.fuelType").value("VLSFO"))
                .andExpect(jsonPath("$.quantityM3").value(850.5))
                .andExpect(jsonPath("$.windowStart").value("2026-10-01T08:00:00Z"))
                .andExpect(jsonPath("$.windowEnd").value("2026-10-01T14:00:00Z"))
                .andExpect(jsonPath("$.status").value(order.status().name()))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T10:15:30Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-28T11:00:00Z"));
    }

    // API-1.1, API-NF-1
    @Test
    void create_validBody_returns201WithLocationAndOrder() throws Exception {
        DispatchOrder created = order(OrderStatus.CREATED);
        when(createOrderUseCase.create(any())).thenReturn(created);

        ResultActions result =
                postOrder(validBody())
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", "/api/v1/orders/" + created.id()))
                        .andExpect(jsonPath("$.cancellationReason").value(nullValue()));
        assertOrderJson(result, created);

        ArgumentCaptor<CreateOrderCommand> command =
                ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(createOrderUseCase).create(command.capture());
        assertThat(command.getValue())
                .isEqualTo(
                        new CreateOrderCommand(
                                new Vessel("Nordic Star", "9321483"),
                                new Berth("B-03"),
                                FuelType.VLSFO,
                                new Quantity(new BigDecimal("850.5")),
                                new DeliveryWindow(WINDOW_START, WINDOW_END)));
    }

    @Test
    void create_quantityWithTrailingZeros_isAccepted() throws Exception {
        when(createOrderUseCase.create(any())).thenReturn(order(OrderStatus.CREATED));
        Map<String, Object> body = validBody();
        body.put("quantityM3", new BigDecimal("1.50000"));

        postOrder(body).andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("vesselName", ""),
                Arguments.of("vesselName", "x".repeat(121)),
                Arguments.of("vesselName", null),
                Arguments.of("vesselImo", "123456"),
                Arguments.of("vesselImo", "12345678"),
                Arguments.of("vesselImo", "12345a7"),
                Arguments.of("vesselImo", null),
                Arguments.of("berth", " "),
                Arguments.of("berth", "B".repeat(21)),
                Arguments.of("berth", null),
                Arguments.of("fuelType", "DIESEL"),
                Arguments.of("fuelType", null),
                Arguments.of("quantityM3", BigDecimal.ZERO),
                Arguments.of("quantityM3", new BigDecimal("-1")),
                Arguments.of("quantityM3", new BigDecimal("10000.001")),
                Arguments.of("quantityM3", new BigDecimal("1.2345")),
                Arguments.of("quantityM3", null),
                Arguments.of("windowStart", null),
                Arguments.of("windowEnd", null),
                Arguments.of("windowEnd", WINDOW_START.toString()),
                Arguments.of("windowEnd", WINDOW_START.minusSeconds(1).toString()));
    }

    // API-1.2
    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidFields")
    void create_invalidField_returns400ListingThatField(String field, Object value)
            throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, value);

        postOrder(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        verifyNoInteractions(createOrderUseCase);
    }

    // API-1.2
    @Test
    void create_everyFieldInvalid_listsEveryFieldSorted() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vesselImo", "1");
        body.put("fuelType", "DIESEL");
        body.put("quantityM3", 0);

        postOrder(body)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[*].field")
                                .value(
                                        contains(
                                                "berth",
                                                "fuelType",
                                                "quantityM3",
                                                "vesselImo",
                                                "vesselName",
                                                "windowEnd",
                                                "windowStart")));
        verifyNoInteractions(createOrderUseCase);
    }

    // API-1.3
    @Test
    void get_existingOrder_returns200WithOrder() throws Exception {
        DispatchOrder approved = order(OrderStatus.APPROVED);
        when(getOrderQuery.get(approved.id())).thenReturn(approved);

        ResultActions result =
                mockMvc.perform(get("/api/v1/orders/{id}", approved.id()))
                        .andExpect(status().isOk());
        assertOrderJson(result, approved);
    }

    @Test
    void get_cancelledOrder_includesCancellationReason() throws Exception {
        DispatchOrder cancelled = order(OrderStatus.CANCELLED);
        when(getOrderQuery.get(cancelled.id())).thenReturn(cancelled);

        mockMvc.perform(get("/api/v1/orders/{id}", cancelled.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Vessel delayed"));
    }

    // API-1.4
    @Test
    void get_unknownOrder_returns404OrderNotFound() throws Exception {
        OrderId id = OrderId.newId();
        when(getOrderQuery.get(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("order " + id + " not found"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // API-1.5
    @Test
    void list_noParameters_usesDefaultsAndReturnsPage() throws Exception {
        DispatchOrder newest = order(OrderStatus.CREATED);
        when(listOrdersQuery.list(any())).thenReturn(new OrderPage(List.of(newest), 0, 20, 1));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(newest.id().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(listOrdersQuery).list(new OrderPageQuery(null, 0, 20));
    }

    // API-1.5
    @Test
    void list_statusAndPaging_passesThemToQueryAndCountsPages() throws Exception {
        when(listOrdersQuery.list(any())).thenReturn(new OrderPage(List.of(), 2, 10, 21));

        mockMvc.perform(get("/api/v1/orders").param("status", "APPROVED"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/orders")
                                .param("status", "APPROVED")
                                .param("page", "2")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(listOrdersQuery).list(new OrderPageQuery(OrderStatus.APPROVED, 0, 20));
        verify(listOrdersQuery).list(new OrderPageQuery(OrderStatus.APPROVED, 2, 10));
    }

    @Test
    void list_emptyResult_hasZeroPages() throws Exception {
        when(listOrdersQuery.list(any())).thenReturn(new OrderPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // API-1.5
    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    void list_pagingOutOfRange_returns400ListingTheParameter(String parameter) throws Exception {
        String[] nameAndValue = parameter.split("=");

        mockMvc.perform(get("/api/v1/orders").param(nameAndValue[0], nameAndValue[1]))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value(nameAndValue[0]));
        verifyNoInteractions(listOrdersQuery);
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(listOrdersQuery);
    }
}
