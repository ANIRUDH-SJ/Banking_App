package com.netbanking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.common.api.ApiErrorWriter;
import com.netbanking.common.api.CorrelationIdFilter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;

class AuthRateLimitFilterTest {

    @Test
    void returnsStructuredTooManyRequestsResponse() throws Exception {
        AuthRateLimitFilter filter = filter(1);
        filter.doFilter(request("203.0.113.8", "198.51.100.1"), response(), new MockFilterChain());
        MockHttpServletRequest rejectedRequest = request("203.0.113.8", "198.51.100.2");
        MockHttpServletResponse rejectedResponse = response();

        filter.doFilter(rejectedRequest, rejectedResponse, new MockFilterChain());

        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(rejectedResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejectedResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(rejectedResponse.getContentType()).isEqualTo("application/json");
        assertThat(rejectedResponse.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"correlationId\":\"test-correlation-id\"");
    }

    @Test
    void ignoresCallerSuppliedForwardingAddress() throws Exception {
        AuthRateLimitFilter filter = filter(1);
        MockHttpServletResponse firstResponse = response();

        filter.doFilter(
                request("203.0.113.8", "198.51.100.1"), firstResponse, new MockFilterChain());
        MockHttpServletResponse secondResponse = response();
        filter.doFilter(
                request("203.0.113.8", "198.51.100.2"), secondResponse, new MockFilterChain());

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotThrottleNonAuthenticationRequests() throws Exception {
        AuthRateLimitFilter filter = filter(1);
        MockHttpServletRequest request = request("203.0.113.8", null);
        request.setRequestURI("/api/v1/accounts");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotThrottleAuthenticationPreflightRequests() throws Exception {
        AuthRateLimitFilter filter = filter(1);
        MockHttpServletRequest request = request("203.0.113.8", null);
        request.setMethod("OPTIONS");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void exhaustedSignInLimitDoesNotBlockPasswordRecovery() throws Exception {
        AuthRateLimitFilter filter = filter(1);
        filter.doFilter(request("203.0.113.8", null), response(), new MockFilterChain());

        MockHttpServletRequest recovery = request("203.0.113.8", null);
        recovery.setRequestURI("/api/v1/auth/password-reset/challenges");
        MockHttpServletResponse recoveryResponse = response();
        filter.doFilter(recovery, recoveryResponse, new MockFilterChain());

        assertThat(recoveryResponse.getStatus()).isEqualTo(200);

        MockHttpServletResponse repeatedRecoveryResponse = response();
        filter.doFilter(recovery, repeatedRecoveryResponse, new MockFilterChain());
        assertThat(repeatedRecoveryResponse.getStatus()).isEqualTo(429);
    }

    private static AuthRateLimitFilter filter(int maxRequests) {
        AuthRateLimiter limiter =
                new AuthRateLimiter(maxRequests, Duration.ofMinutes(1), Clock.systemUTC());
        return new AuthRateLimitFilter(
                limiter, new ApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddress);
        request.setAttribute(CorrelationIdFilter.ATTRIBUTE, "test-correlation-id");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
