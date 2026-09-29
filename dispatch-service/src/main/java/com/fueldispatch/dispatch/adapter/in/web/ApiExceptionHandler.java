package com.fueldispatch.dispatch.adapter.in.web;

import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.domain.InvalidOrderTransitionException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 ProblemDetail bodies with a machine-readable {@code code}. Spring's
 * own exceptions (malformed requests, type mismatches) keep the default ProblemDetail from {@link
 * ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Comparator<InvalidField> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message);

    /** One invalid input in a {@code VALIDATION_FAILED} body. */
    record InvalidField(String field, String message) {}

    /** API-1.4 */
    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleOrderNotFound(OrderNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND, "Order not found", exception.getMessage(), "ORDER_NOT_FOUND");
    }

    /** API-2.3 */
    @ExceptionHandler(InvalidOrderTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidOrderTransitionException exception) {
        ProblemDetail problem =
                problem(
                        HttpStatus.CONFLICT,
                        "Invalid order transition",
                        "Cannot "
                                + exception.action()
                                + " an order in status "
                                + exception.currentStatus(),
                        "INVALID_TRANSITION");
        problem.setProperty("currentStatus", exception.currentStatus());
        problem.setProperty("action", exception.action());
        return problem;
    }

    /** API-1.2: invalid request body. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<InvalidField> fields =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
                        .toList();
        return validationFailed(fields);
    }

    /** API-1.5: invalid query or path parameters. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<InvalidField> fields = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                fields.add(new InvalidField(parameter, error.getDefaultMessage()));
            }
        }
        return validationFailed(fields);
    }

    private static ResponseEntity<Object> validationFailed(List<InvalidField> fields) {
        ProblemDetail body =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Invalid request",
                        "Request has " + fields.size() + " invalid field(s)",
                        "VALIDATION_FAILED");
        body.setProperty("errors", fields.stream().sorted(BY_FIELD_THEN_MESSAGE).toList());
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
