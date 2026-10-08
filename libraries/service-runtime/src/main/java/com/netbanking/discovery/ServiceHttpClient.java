package com.netbanking.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Component
public class ServiceHttpClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ServiceResolver resolver;
    private final RestClient client;

    public ServiceHttpClient(
            ServiceResolver resolver,
            @Value("${spring.application.name}") String service,
            @Value("${app.internal.token}") String token) {
        if (token.length() < 32 || token.startsWith("REPLACE_"))
            throw new IllegalStateException(
                    "A service token of at least 32 characters is required.");
        this.resolver = resolver;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client =
                RestClient.builder()
                        .requestFactory(factory)
                        .defaultHeader("X-Service-Name", service)
                        .defaultHeader("X-Service-Token", token)
                        .build();
    }

    public <T> T get(String service, String path, Class<T> type) {
        return invoke(
                () ->
                        client.get()
                                .uri(resolver.resolve(service).resolve(path))
                                .retrieve()
                                .body(type));
    }

    public <T> T post(String service, String path, Object body, Class<T> type) {
        return invoke(
                () ->
                        client.post()
                                .uri(resolver.resolve(service).resolve(path))
                                .body(body)
                                .retrieve()
                                .body(type));
    }

    private <T> T invoke(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            throw rejected(e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service unavailable; retry using the same operation key.");
        }
    }

    private static DownstreamRejectedException rejected(RestClientResponseException e) {
        String code = null;
        String detail = null;
        try {
            JsonNode body = JSON.readTree(e.getResponseBodyAsString());
            if (body != null && body.isObject()) {
                code = body.path("code").isTextual() ? body.path("code").asText() : null;
                detail = body.path("message").isTextual() ? body.path("message").asText() : null;
            }
        } catch (java.io.IOException unreadable) {
            // Non-JSON error bodies carry no contract; keep the status only.
        }
        long retryAfter = 0;
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        if (header != null && header.matches("[0-9]{1,9}")) {
            retryAfter = Long.parseLong(header);
        }
        return new DownstreamRejectedException(e.getStatusCode(), code, detail, retryAfter);
    }
}
