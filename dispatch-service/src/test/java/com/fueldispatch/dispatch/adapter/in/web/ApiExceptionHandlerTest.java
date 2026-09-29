package com.fueldispatch.dispatch.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.application.port.in.ChangeOrderStatusUseCase;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.in.GetOrderQuery;
import com.fueldispatch.dispatch.application.port.in.ListOrdersQuery;
import com.fueldispatch.dispatch.domain.DomainValidationException;
import com.fueldispatch.dispatch.domain.InvalidOrderTransitionException;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.OrderId;
import com.fueldispatch.dispatch.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Every row of the error-code table in the phase 02 design (API-1.2, 1.4, 2.3, 2.4). */
@WebMvcTest(OrderController.class)
class ApiExceptionHandlerTest {

    private static final String VALID_BODY =
            """
            {"vesselName": "Nordic Star", "vesselImo": "9321483", "berth": "B-03",
             "fuelType": "VLSFO", "quantityM3": 850.5,
             "windowStart": "2026-10-01T08:00:00Z", "windowEnd": "2026-10-01T14:00:00Z"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreateOrderUseCase createOrderUseCase;
    @MockitoBean private GetOrderQuery getOrderQuery;
    @MockitoBean private ListOrdersQuery listOrdersQuery;
    @MockitoBean private ChangeOrderStatusUseCase changeOrderStatusUseCase;

    private static MockHttpServletRequestBuilder postJson(String path, String json) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** The ProblemDetail shape of the design: type, title, status, detail, code, media type. */
    private static ResultActions expectProblem(
            ResultActions result, int status, String title, String detail, String code)
            throws Exception {
        return result.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.code").value(code));
    }

    // API-1.2: Bean Validation
    @Test
    void beanValidation_returnsValidationFailedWithErrors() throws Exception {
        expectProblem(
                        mockMvc.perform(
                                postJson("/api/v1/orders", VALID_BODY.replace("9321483", "1"))),
                        400,
                        "Invalid request",
                        "Request has 1 invalid field(s)",
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("vesselImo"))
                .andExpect(jsonPath("$.errors[0].message").value("must be exactly 7 digits"));
    }

    // API-1.2: wrong JSON type for a known field
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "quantityM3 | \"quantityM3\": 850.5 | \"quantityM3\": \"abc\"",
                "quantityM3 | \"quantityM3\": 850.5 | \"quantityM3\": {}",
                "windowStart | \"windowStart\": \"2026-10-01T08:00:00Z\" | \"windowStart\":"
                        + " \"soon\""
            })
    void wrongJsonType_returnsValidationFailedNamingTheField(
            String field, String valid, String wrongType) throws Exception {
        expectProblem(
                        mockMvc.perform(
                                postJson("/api/v1/orders", VALID_BODY.replace(valid, wrongType))),
                        400,
                        "Invalid request",
                        "Request has 1 invalid field(s)",
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    // API-1.2: bad UUID in the path
    @Test
    void badUuidInPath_returnsValidationFailedNamingId() throws Exception {
        expectProblem(
                        mockMvc.perform(get("/api/v1/orders/not-a-uuid")),
                        400,
                        "Invalid request",
                        "Request has 1 invalid field(s)",
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    // API-1.5: unknown status filter
    @Test
    void unknownStatus_returnsValidationFailedNamingStatus() throws Exception {
        expectProblem(
                        mockMvc.perform(get("/api/v1/orders").param("status", "LOST")),
                        400,
                        "Invalid request",
                        "Request has 1 invalid field(s)",
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    // API-1.2: domain rule as a safety net
    @Test
    void domainValidationException_returnsValidationFailedWithoutErrors() throws Exception {
        when(createOrderUseCase.create(any()))
                .thenThrow(new DomainValidationException("berth must not be blank"));

        expectProblem(
                        mockMvc.perform(postJson("/api/v1/orders", VALID_BODY)),
                        400,
                        "Invalid request",
                        "berth must not be blank",
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void unparseableJson_returnsMalformedRequest() throws Exception {
        expectProblem(
                mockMvc.perform(postJson("/api/v1/orders", "{\"vesselName\": ")),
                400,
                "Malformed request",
                "Request body is missing or is not valid JSON",
                "MALFORMED_REQUEST");
    }

    @Test
    void missingBody_returnsMalformedRequest() throws Exception {
        expectProblem(
                mockMvc.perform(
                        post("/api/v1/orders/{id}/cancel", OrderId.newId())
                                .contentType(MediaType.APPLICATION_JSON)),
                400,
                "Malformed request",
                "Request body is missing or is not valid JSON",
                "MALFORMED_REQUEST");
    }

    // API-1.4
    @Test
    void orderNotFound_returnsOrderNotFound() throws Exception {
        OrderId id = OrderId.newId();
        when(getOrderQuery.get(id)).thenThrow(new OrderNotFoundException(id));

        expectProblem(
                mockMvc.perform(get("/api/v1/orders/{id}", id)),
                404,
                "Order not found",
                "order " + id + " not found",
                "ORDER_NOT_FOUND");
    }

    // API-2.3
    @Test
    void invalidTransition_returnsInvalidTransitionWithStatusAndAction() throws Exception {
        when(changeOrderStatusUseCase.changeStatus(any()))
                .thenThrow(
                        new InvalidOrderTransitionException(
                                OrderStatus.DELIVERED, OrderAction.APPROVE));

        expectProblem(
                        mockMvc.perform(post("/api/v1/orders/{id}/approve", OrderId.newId())),
                        409,
                        "Invalid order transition",
                        "Cannot APPROVE an order in status DELIVERED",
                        "INVALID_TRANSITION")
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.action").value("APPROVE"));
    }

    // API-2.4
    @Test
    void optimisticLockingFailure_returnsConcurrentModification() throws Exception {
        when(changeOrderStatusUseCase.changeStatus(any()))
                .thenThrow(new OptimisticLockingFailureException("row was updated"));

        expectProblem(
                mockMvc.perform(post("/api/v1/orders/{id}/approve", OrderId.newId())),
                409,
                "Concurrent modification",
                "The order was changed by another request; reload the order and retry",
                "CONCURRENT_MODIFICATION");
    }

    @Test
    void methodNotAllowed_codeIsTheStatusName() throws Exception {
        mockMvc.perform(delete("/api/v1/orders"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaType_codeIsTheStatusName() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownRoute_codeIsTheStatusName() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedException_returnsInternalErrorWithoutLeakingDetails() throws Exception {
        when(listOrdersQuery.list(any()))
                .thenThrow(new IllegalStateException("password=hunter2 at db-01"));

        expectProblem(
                        mockMvc.perform(get("/api/v1/orders")),
                        500,
                        "Internal error",
                        "Unexpected error; the server log has the details",
                        "INTERNAL_ERROR")
                .andExpect(content().string(not(containsString("hunter2"))));
    }
}
