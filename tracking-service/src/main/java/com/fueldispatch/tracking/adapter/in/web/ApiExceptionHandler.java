package com.fueldispatch.tracking.adapter.in.web;

import com.fueldispatch.tracking.application.TrackingNotFoundException;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Turns every error into an RFC 9457 ProblemDetail with a machine-readable {@code code}, with the
 * same shape as dispatch-service. WebFlux errors without a code of their own (405, 406, unknown
 * route, ...) get the name of their HTTP status.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** One invalid input in a {@code VALIDATION_FAILED} body. */
    record InvalidField(String field, String message) {}

    /** TRK-2.2 */
    @ExceptionHandler(TrackingNotFoundException.class)
    ProblemDetail handleTrackingNotFound(TrackingNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Tracking not found",
                exception.getMessage(),
                "TRACKING_NOT_FOUND");
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

    /** A required path / query parameter is missing. */
    @Override
    protected Mono<ResponseEntity<Object>> handleMissingRequestValueException(
            MissingRequestValueException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            ServerWebExchange exchange) {
        return validationFailed(exception.getName(), "is required");
    }

    /**
     * A badly typed path / query parameter (bad UUID, unknown status) is a validation error on that
     * parameter; other input errors keep WebFlux's own handling.
     */
    @Override
    protected Mono<ResponseEntity<Object>> handleServerWebInputException(
            ServerWebInputException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            ServerWebExchange exchange) {
        MethodParameter parameter = exception.getMethodParameter();
        if (parameter == null || parameter.getParameterName() == null) {
            return super.handleServerWebInputException(exception, headers, status, exchange);
        }
        return validationFailed(parameter.getParameterName(), "has an invalid value");
    }

    private static Mono<ResponseEntity<Object>> validationFailed(String field, String message) {
        ProblemDetail body =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Invalid request",
                        "Request has 1 invalid field(s)",
                        "VALIDATION_FAILED");
        body.setProperty("errors", List.of(new InvalidField(field, message)));
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    /** Adds {@code code} = HTTP status name to WebFlux errors that have none. */
    @Override
    protected Mono<ResponseEntity<Object>> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            ServerWebExchange exchange) {
        return super.handleExceptionInternal(exception, body, headers, statusCode, exchange)
                .doOnNext(
                        response -> {
                            if (response.getBody() instanceof ProblemDetail problem
                                    && (problem.getProperties() == null
                                            || !problem.getProperties().containsKey("code"))) {
                                HttpStatus status = HttpStatus.resolve(statusCode.value());
                                problem.setProperty(
                                        "code",
                                        status != null
                                                ? status.name()
                                                : "HTTP_" + statusCode.value());
                            }
                        });
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
