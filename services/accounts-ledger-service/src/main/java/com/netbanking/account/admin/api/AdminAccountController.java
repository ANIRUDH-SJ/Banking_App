package com.netbanking.account.admin.api;

import com.netbanking.account.admin.service.AdminAccountService;
import com.netbanking.account.api.AccountStatus;
import com.netbanking.common.api.PagedResponse;
import com.netbanking.security.SecurityContextHelper;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accounts;

    public AdminAccountController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public PagedResponse<AdminAccountResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(accounts.search(query, customerId, status, page, size));
    }

    @PatchMapping("/{accountId}/status")
    public AdminAccountResponse changeStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AdminAccountStatusRequest request) {
        return accounts.changeStatus(
                SecurityContextHelper.currentUserId(), accountId, request.status());
    }
}
