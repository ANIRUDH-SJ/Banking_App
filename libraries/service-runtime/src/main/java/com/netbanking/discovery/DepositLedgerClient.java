package com.netbanking.discovery;

import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.LedgerReceipt;

import org.springframework.stereotype.Service;

@Service
public class DepositLedgerClient {
    private final ServiceHttpClient client;

    public DepositLedgerClient(ServiceHttpClient client) {
        this.client = client;
    }

    public LedgerReceipt post(DepositLedgerCommand command) {
        return client.post(
                "accounts-ledger-service", "/internal/ledger/deposits", command, LedgerReceipt.class);
    }
}
