package com.netbanking.gateway;

import com.netbanking.common.api.ApiErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final ApiErrorWriter errors;

    public AuthRateLimitFilter(AuthRateLimiter limiter, ApiErrorWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthRateLimiter.Decision decision = limiter.acquire(clientKey(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        errors.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                "Too many authentication requests. Try again later.");
    }

    private static String clientKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String client = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        String operation = request.getRequestURI().startsWith("/api/v1/auth/password-reset/")
                ? "password-reset"
                : "authentication";
        return client + ":" + operation;
    }
}
