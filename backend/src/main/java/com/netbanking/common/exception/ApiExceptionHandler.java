package com.netbanking.common.exception;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(ConflictException exception) { return Map.of("message", exception.getMessage()); }
    @ExceptionHandler({UnauthorizedException.class}) @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> unauthorized(RuntimeException exception) { return Map.of("message", exception.getMessage()); }
    @ExceptionHandler(ResourceNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(ResourceNotFoundException exception) { return Map.of("message", exception.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(IllegalArgumentException exception) { return Map.of("message", exception.getMessage()); }
    @ExceptionHandler(SecurityException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> forbidden(SecurityException exception) { return Map.of("message", exception.getMessage()); }
    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> illegalState(IllegalStateException exception) { return Map.of("message", exception.getMessage()); }
}
