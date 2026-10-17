package com.learning.discovery.order.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves this service as RFC 9457 {@code application/problem+json}
 * ({@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}).
 * Extending {@link ResponseEntityExceptionHandler} covers Spring MVC's own exceptions
 * (ResponseStatusException 404s, type mismatches, 405, unreadable bodies, ...) and makes
 * Boot's ProblemDetailsExceptionHandler back off, so {@code spring.mvc.problemdetails.enabled}
 * isn't needed. The catch-all keeps stack traces out of responses; they go to the log.
 */
@Slf4j
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error, see the order-service logs");
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
