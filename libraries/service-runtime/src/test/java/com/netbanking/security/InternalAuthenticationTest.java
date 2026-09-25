package com.netbanking.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

class InternalAuthenticationTest {
    @Test
    void rejectsImpersonationAndDoesNotAcceptAHashAsABearerSecret() throws Exception {
        String secret = UUID.randomUUID().toString();
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(secret.getBytes(StandardCharsets.UTF_8)));
        InternalTokens tokens = new InternalTokens();
        tokens.setTokenHashes(Map.of("payments-service", hash));
        assertThat(tokens.accepts("payments-service", secret)).isTrue();
        assertThat(tokens.accepts("products-service", secret)).isFalse();
        assertThat(tokens.accepts("payments-service", hash)).isFalse();
        var filter = new InternalAuthenticationFilter(tokens);
        var request = new MockHttpServletRequest();
        request.setServletPath("/internal/ledger/operations");
        request.addHeader("X-Service-Name", "payments-service");
        request.addHeader("X-Service-Token", "wrong");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        SecurityContextHolder.clearContext();
    }
}
