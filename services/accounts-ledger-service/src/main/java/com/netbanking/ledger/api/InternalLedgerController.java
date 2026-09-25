package com.netbanking.ledger.api;

import com.netbanking.account.service.AccountService;
import com.netbanking.contracts.*;
import com.netbanking.ledger.service.LedgerService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@PreAuthorize("hasAnyAuthority('SERVICE_payments-service','SERVICE_products-service')")
public class InternalLedgerController {
    private final LedgerService ledger;
    private final AccountService accounts;

    public InternalLedgerController(LedgerService ledger, AccountService accounts) {
        this.ledger = ledger;
        this.accounts = accounts;
    }

    @GetMapping("/accounts/{accountId}/owners/{userId}")
    public AccountSnapshot account(@PathVariable Long accountId, @PathVariable Long userId) {
        var a = accounts.getOwnedAccount(userId, accountId);
        return new AccountSnapshot(
                a.accountId(),
                a.accountNumber(),
                a.currencyCode().trim(),
                a.accountStatus(),
                a.availableBalance());
    }

    @PostMapping("/ledger/operations")
    public LedgerReceipt post(@Valid @RequestBody LedgerCommand command, Authentication caller) {
        return ledger.post(caller.getName(), command);
    }
}
