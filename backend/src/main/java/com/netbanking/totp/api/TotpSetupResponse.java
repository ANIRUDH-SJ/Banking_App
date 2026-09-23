package com.netbanking.totp.api;
public record TotpSetupResponse(
        String provisioningUri,
        String qrCodeDataUri,
        String manualEntryKey,
        String issuer,
        String accountName) {
}
