package com.netbanking.transaction.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class TransactionReferenceGenerator {

    public String generate() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
