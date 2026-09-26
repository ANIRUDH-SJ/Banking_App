package com.netbanking.customer.api;

public record CustomerProfileResponse(
        Long customerId,
        String customerNumber,
        String firstName,
        String lastName,
        String mobileNumber,
        String kycStatus,
        boolean active) {}
