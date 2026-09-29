package com.fueldispatch.dispatch.adapter.in.web;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fueldispatch.dispatch.application.OrderNotFoundException;
import com.fueldispatch.dispatch.domain.DomainValidationException;
import com.fueldispatch.dispatch.domain.InvalidOrderTransitionException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 ProblemDetail with a machine-readable {@code code}, following
 * the error-code table in the phase 02 design. Spring MVC errors without a code of their own (405,
 * 415, unknown route, ...) get the name of their HTTP status.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_VALUE = "has an invalid value";

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

    /** API-2.4: another transaction committed first (optimistic locking). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The order was changed by another request; reload the order and retry",
                "CONCURRENT_MODIFICATION");
    }

    /** API-1.2: a domain rule the request DTOs did not catch (safety net). */
    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleDomainValidation(DomainValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                exception.getMessage(),
                "VALIDATION_FAILED");
    }

    /** Last resort: log the cause, never expose it. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        logger.error("Unexpected error while handling a request", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "Unexpected error; the server log has the details",
                "INTERNAL_ERROR");
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

    /** A path or query parameter of the wrong type (bad UUID, unknown status). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationFailed(
                List.of(new InvalidField(exception.getPropertyName(), INVALID_VALUE)));
    }

    /**
     * A JSON value of the wrong type for a known field is a validation error on that field;
     * anything else (syntax error, missing body) is a malformed request.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (exception.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            return validationFailed(List.of(new InvalidField(fieldPath(mismatch), INVALID_VALUE)));
        }
        return ResponseEntity.badRequest()
                .body(
                        problem(
                                HttpStatus.BAD_REQUEST,
                                "Malformed request",
                                "Request body is missing or is not valid JSON",
                                "MALFORMED_REQUEST"));
    }

    /** Adds {@code code} = HTTP status name to Spring MVC errors that have none. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null
                        || !problem.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty(
                    "code", status != null ? status.name() : "HTTP_" + statusCode.value());
        }
        return response;
    }

    private static String fieldPath(MismatchedInputException mismatch) {
        return mismatch.getPath().stream()
                .map(
                        reference ->
                                reference.getFieldName() != null
                                        ? reference.getFieldName()
                                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
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
