package com.netbanking.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netbanking.discovery.ServiceResolver;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.*;
import java.net.http.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {
    static final KeyPair KEYS = keys();
    @LocalServerPort int port;
    @MockitoBean ServiceResolver resolver;
    HttpServer upstream;
    final AtomicReference<String> forwardedToken = new AtomicReference<>();
    final AtomicReference<String> internalToken = new AtomicReference<>();
    final HttpClient client = HttpClient.newHttpClient();

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add(
                "app.security.jwt.public-key",
                () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        p.add("app.internal.token", () -> "test-only-gateway-token-1234567890");
        for (String name :
                List.of(
                        "identity-service",
                        "accounts-ledger-service",
                        "payments-service",
                        "products-service",
                        "notification-service",
                        "audit-reporting-service"))
            p.add("app.internal.token-hashes." + name, () -> "a".repeat(64));
        p.add("eureka.client.enabled", () -> false);
        p.add("app.scheduling.enabled", () -> false);
    }

    @BeforeEach
    void server() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext(
                "/api/v1/accounts",
                exchange -> {
                    forwardedToken.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    internalToken.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
                    byte[] body =
                            "[{\"accountId\":7}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        upstream.start();
        when(resolver.resolve("accounts-ledger-service"))
                .thenReturn(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
    }

    @AfterEach
    void stop() {
        upstream.stop(0);
    }

    String token() {
        return Jwts.builder()
                .issuer("banking-identity")
                .audience()
                .add("banking-api")
                .and()
                .subject("7")
                .claim("token_use", "access")
                .claim("roles", List.of("CUSTOMER"))
                .claim("username", "test")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    HttpResponse<String> get(String path, String token) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("X-Service-Name", "payments-service")
                        .header("X-Service-Token", "injected");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void verifiesJwtRoutesRequestAndStripsInternalCredentials() throws Exception {
        String token = token();
        var result = get("/api/v1/accounts", token);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("accountId");
        assertThat(forwardedToken.get()).isEqualTo("Bearer " + token);
        assertThat(internalToken.get()).isNull();
        assertThat(result.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @Test
    void anonymousAndInternalRoutesCannotReachUpstream() throws Exception {
        assertThat(get("/api/v1/accounts", null).statusCode()).isEqualTo(401);
        assertThat(get("/internal/ledger/operations", token()).statusCode()).isIn(401, 403, 404);
        verifyNoInteractions(resolver);
    }

    @Test
    void missingServiceAndConnectionFailureReturnUnavailable() throws Exception {
        when(resolver.resolve("accounts-ledger-service"))
                .thenThrow(
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(get("/api/v1/accounts", token()).statusCode()).isEqualTo(503);
    }
}
