package com.webstore.api.common;

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns exceptions into JSON error responses (RFC 9457 problem details).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** SQLSTATE used by SIGNAL in the stored procedures for business rule errors. */
    private static final String USER_DEFINED_SQLSTATE = "45000";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (a, b) -> a));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidation(HandlerMethodValidationException e) {
        Map<String, String> errors = e.getParameterValidationResults().stream()
                .collect(Collectors.toMap(
                        r -> r.getMethodParameter().getParameterName(),
                        r -> String.valueOf(r.getResolvableErrors().getFirst().getDefaultMessage()),
                        (a, b) -> a));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Optimistic locking: Hibernate's "update ... where id=? and version=?" matched no
     * row, because another transaction changed the row first.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The data was changed by someone else in the meantime. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    /**
     * Errors raised with SIGNAL SQLSTATE '45000' in a procedure carry a readable
     * message, e.g. 'Insufficient stock'. Map them to a matching HTTP status.
     */
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(DataAccessException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql
                && USER_DEFINED_SQLSTATE.equals(sql.getSQLState())) {
            String message = sql.getMessage();
            // The driver may prefix the message with connection details; keep only the text.
            int idx = message.lastIndexOf(") ");
            String detail = idx >= 0 ? message.substring(idx + 2) : message;
            return ProblemDetail.forStatusAndDetail(statusFor(detail), detail);
        }
        throw e;
    }

    private static HttpStatus statusFor(String detail) {
        if (detail.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }
        if (detail.contains("Insufficient stock") || detail.contains("can be cancelled")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
