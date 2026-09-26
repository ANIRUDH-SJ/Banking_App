package com.netbanking.discovery;

import com.netbanking.contracts.*;

import org.springframework.stereotype.Service;

@Service
public class LedgerClient {
    private final ServiceHttpClient client;

    public LedgerClient(ServiceHttpClient client) {
        this.client = client;
    }

    public AccountSnapshot account(Long userId, Long accountId) {
        return client.get(
                "accounts-ledger-service",
                "/internal/accounts/" + accountId + "/owners/" + userId,
                AccountSnapshot.class);
    }

    public LedgerReceipt post(LedgerCommand command) {
        return client.post(
                "accounts-ledger-service",
                "/internal/ledger/operations",
                command,
                LedgerReceipt.class);
    }
}
