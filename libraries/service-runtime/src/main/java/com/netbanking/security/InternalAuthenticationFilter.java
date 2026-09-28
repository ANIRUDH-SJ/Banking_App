package com.netbanking.security;

import com.netbanking.common.api.ApiErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class InternalAuthenticationFilter extends OncePerRequestFilter {
    private final InternalTokens tokens;
    private final ApiErrorWriter errors;

    public InternalAuthenticationFilter(InternalTokens tokens, ApiErrorWriter errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getServletPath().startsWith("/internal/")) {
            String caller = request.getHeader("X-Service-Name");
            if (caller == null || !tokens.accepts(caller, request.getHeader("X-Service-Token"))) {
                errors.write(
                        request,
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Service authentication is required.");
                return;
            }
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    caller,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_SERVICE"),
                                            new SimpleGrantedAuthority("SERVICE_" + caller))));
        }
        chain.doFilter(request, response);
    }
}
