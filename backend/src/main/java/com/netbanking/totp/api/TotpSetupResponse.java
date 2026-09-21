package com.netbanking.totp.api;
public record TotpSetupResponse(String provisioningUri, String issuer, String accountName) { }
