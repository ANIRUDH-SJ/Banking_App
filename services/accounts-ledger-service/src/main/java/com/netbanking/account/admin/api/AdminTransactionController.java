package com.netbanking.account.admin.api;

import com.netbanking.account.admin.service.AdminTransactionService;
import com.netbanking.common.api.PagedResponse;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {
    private final AdminTransactionService transactions;

    public AdminTransactionController(AdminTransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping
    public PagedResponse<AdminTransactionResponse> search(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long initiatedByUserId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(
                transactions.search(
                        new AdminTransactionSearchFilter(
                                reference,
                                accountId,
                                initiatedByUserId,
                                type,
                                status,
                                from,
                                to),
                        page,
                        size));
    }
}
