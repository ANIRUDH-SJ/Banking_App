package com.netbanking.account.api;

import com.netbanking.account.service.AccountService;
import com.netbanking.account.service.ForeignCurrencyAccountService;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService service;
    private final ForeignCurrencyAccountService foreignCurrencies;

    public AccountController(AccountService service, ForeignCurrencyAccountService foreignCurrencies) {
        this.service = service;
        this.foreignCurrencies = foreignCurrencies;
    }

    @GetMapping
    public List<AccountSummaryResponse> accounts() {
        return service.getAccountsForUser(SecurityContextHelper.currentUserId());
    }

    @GetMapping("/{accountId}")
    public AccountSummaryResponse account(@PathVariable Long accountId) {
        return service.getOwnedAccount(SecurityContextHelper.currentUserId(), accountId);
    }

    @PostMapping("/foreign-currency")
    public AccountSummaryResponse openForeignCurrency(
            @Valid @RequestBody OpenForeignCurrencyAccountRequest request) {
        return foreignCurrencies.open(SecurityContextHelper.currentUserId(), request.currencyCode());
    }
}
