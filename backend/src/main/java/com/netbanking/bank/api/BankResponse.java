package com.netbanking.bank.api;

public record BankResponse(Long bankId, String bankCode, String legalName, String displayName) {
}
