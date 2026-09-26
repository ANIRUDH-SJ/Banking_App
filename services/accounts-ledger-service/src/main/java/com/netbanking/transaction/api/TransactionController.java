package com.netbanking.transaction.api;

import com.netbanking.security.SecurityContextHelper;
import com.netbanking.transaction.service.TransactionService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public Page<TransactionResponse> list(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return transactionService.getAccountTransactions(
                SecurityContextHelper.currentUserId(), accountId, page, size);
    }

    @GetMapping("/{entryId}")
    public TransactionResponse get(@PathVariable Long accountId, @PathVariable Long entryId) {
        return transactionService.getAccountTransaction(
                SecurityContextHelper.currentUserId(), accountId, entryId);
    }

    @GetMapping("/{entryId}/status-history")
    public List<TransactionStatusHistoryResponse> statusHistory(
            @PathVariable Long accountId, @PathVariable Long entryId) {
        return transactionService.getStatusHistory(
                SecurityContextHelper.currentUserId(), accountId, entryId);
    }
}
