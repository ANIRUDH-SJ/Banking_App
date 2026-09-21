package com.netbanking.security;
import com.netbanking.common.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;
public final class SecurityContextHelper {
    private SecurityContextHelper() { }
    public static Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtService.JwtPrincipal jwtPrincipal) return jwtPrincipal.userId();
        throw new UnauthorizedException("Authentication is required.");
    }
}
