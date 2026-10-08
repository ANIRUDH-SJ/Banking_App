package com.netbanking.common.exception;

import com.netbanking.common.api.ApiErrorResponse;
import com.netbanking.common.api.ApiErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final ApiErrorWriter errors;

    public ApiExceptionHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse conflict(ConflictException exception, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({UnauthorizedException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiErrorResponse unauthorized(RuntimeException exception, HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage());
    }

    @ExceptionHandler(VerificationFailedException.class)
    ResponseEntity<ApiErrorResponse> verificationFailed(
            VerificationFailedException exception, HttpServletRequest request) {
        HttpStatus status = exception.locked() ? HttpStatus.LOCKED : HttpStatus.UNPROCESSABLE_ENTITY;
        Map<String, String> fields =
                exception.field() == null ? Map.of() : Map.of(exception.field(), exception.getMessage());
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (exception.locked()) {
            response.header("Retry-After", Long.toString(exception.retryAfterSeconds()));
        }
        return response.body(
                errors.response(
                        request, status.value(), exception.code(), exception.getMessage(), fields));
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ApiErrorResponse> accountLocked(
            AccountLockedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .cacheControl(CacheControl.noStore())
                .body(response(request, HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", exception.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiErrorResponse notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiErrorResponse forbidden(RuntimeException exception, HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse illegalState(IllegalStateException exception, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "INVALID_STATE", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError field : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(field.getField(), field.getDefaultMessage());
        }
        return errors.response(
                request,
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed.",
                fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse constraint(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations()
                .forEach(
                        violation ->
                                fields.putIfAbsent(
                                        violation.getPropertyPath().toString(),
                                        violation.getMessage()));
        return errors.response(
                request,
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed.",
                fields);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> responseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        String message =
                exception.getReason() == null
                        ? "Request could not be completed."
                        : exception.getReason();
        ApiErrorResponse body =
                errors.response(
                        request,
                        exception.getStatusCode().value(),
                        "UPSTREAM_REQUEST_FAILED",
                        message,
                        Map.of());
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    ApiErrorResponse unexpected(Exception exception, HttpServletRequest request) {
        return response(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    private ApiErrorResponse response(
            HttpServletRequest request, HttpStatus status, String code, String message) {
        return errors.response(request, status.value(), code, message, Map.of());
    }
}
