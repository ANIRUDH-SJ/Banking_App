package com.netbanking.user.admin.api;

public record AdminCustomerResponse(
        Long customerId,
        String customerNumber,
        String firstName,
        String lastName,
        String mobileNumber,
        String kycStatus,
        boolean active) {}
