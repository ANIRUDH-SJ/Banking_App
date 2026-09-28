package com.netbanking.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiErrorResponse response(
            HttpServletRequest request,
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                request.getRequestURI(),
                CorrelationIdFilter.current(request),
                fieldErrors);
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(), response(request, status, code, message, Map.of()));
    }
}
