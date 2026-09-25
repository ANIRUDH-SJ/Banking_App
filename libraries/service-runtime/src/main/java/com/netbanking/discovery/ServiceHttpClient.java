package com.netbanking.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class ServiceHttpClient {
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
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
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
            throw new ResponseStatusException(
                    e.getStatusCode(), "Downstream service rejected the operation.");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service unavailable; retry using the same operation key.");
        }
    }
}
