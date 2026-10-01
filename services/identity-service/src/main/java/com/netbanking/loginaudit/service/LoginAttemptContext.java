package com.netbanking.loginaudit.service;

import jakarta.servlet.http.HttpServletRequest;

public record LoginAttemptContext(String clientIpAddress, String userAgent) {
    public static LoginAttemptContext from(HttpServletRequest request) {
        return new LoginAttemptContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
