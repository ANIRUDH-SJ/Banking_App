package com.netbanking.user.admin.api;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long userId,
        String username,
        String email,
        String status,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant lastLoginAt,
        List<String> roles,
        AdminCustomerResponse customer) {}
