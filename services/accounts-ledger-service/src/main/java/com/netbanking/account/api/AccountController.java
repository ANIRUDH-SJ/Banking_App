package com.netbanking.account.api;

import com.netbanking.account.service.AccountService;
import com.netbanking.security.SecurityContextHelper;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountSummaryResponse> accounts() {
        return service.getAccountsForUser(SecurityContextHelper.currentUserId());
    }

    @GetMapping("/{accountId}")
    public AccountSummaryResponse account(@PathVariable Long accountId) {
        return service.getOwnedAccount(SecurityContextHelper.currentUserId(), accountId);
    }
}
