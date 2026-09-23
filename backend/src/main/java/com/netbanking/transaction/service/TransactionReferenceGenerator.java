package com.netbanking.transaction.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TransactionReferenceGenerator {

    public String generate() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
