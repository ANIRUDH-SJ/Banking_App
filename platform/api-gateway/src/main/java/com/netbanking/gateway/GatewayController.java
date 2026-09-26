package com.netbanking.gateway;

import com.netbanking.discovery.ServiceResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@RestController
public class GatewayController {
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private final GatewayRoutes routes;
    private final ServiceResolver resolver;
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public GatewayController(GatewayRoutes routes, ServiceResolver resolver) {
        this.routes = routes;
        this.resolver = resolver;
    }

    @RequestMapping("/api/v1/**")
    public void forward(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
                .contains(request.getMethod())) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
        }
        String path = request.getRequestURI();
        URI base = resolver.resolve(routes.serviceFor(path));
        String query = request.getQueryString();
        URI target = base.resolve(path + (query == null ? "" : "?" + query));
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        var outgoing =
                HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(30))
                        .method(
                                request.getMethod(),
                                body.length == 0
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofByteArray(body));
        // Never relay caller-supplied internal credentials, cookies, Host or forwarding headers.
        for (String name : List.of("Authorization", "Content-Type", "Accept")) {
            String value = request.getHeader(name);
            if (value != null) outgoing.header(name, value);
        }
        try {
            var upstream = client.send(outgoing.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] result;
            try (var input = upstream.body()) {
                result = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (result.length > MAX_RESPONSE_BYTES)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            response.setStatus(upstream.statusCode());
            response.setHeader("Cache-Control", "no-store");
            for (String name : List.of("Content-Type", "Content-Disposition", "Retry-After")) {
                upstream.headers()
                        .firstValue(name)
                        .ifPresent(value -> response.setHeader(name, value));
            }
            response.getOutputStream().write(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Service request interrupted");
        } catch (IOException unavailable) {
            // The caller must retry mutations with the same idempotency key after an ambiguous
            // timeout.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");
        }
    }
}
