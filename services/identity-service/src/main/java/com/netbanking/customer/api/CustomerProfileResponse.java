package com.netbanking.customer.api;

import java.time.LocalDate;

public record CustomerProfileResponse(
        Long customerId,
        String customerNumber,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String mobileNumber,
        String kycStatus,
        boolean active) {}
