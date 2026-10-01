package com.netbanking.biller.provider;

public record BillerPaymentReceipt(String providerReference, String status, String reason) {
    public BillerPaymentReceipt {
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Biller provider reference is required.");
        }
        if (!"ACCEPTED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("Biller provider status is invalid.");
        }
        if ("REJECTED".equals(status) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Biller rejection reason is required.");
        }
    }

    public boolean accepted() {
        return "ACCEPTED".equals(status);
    }

    public boolean rejected() {
        return "REJECTED".equals(status);
    }
}
