package com.netbanking.auth.api;

public record RegistrationResponse(
        Long userId, Long customerId, String customerNumber, String status) {}
