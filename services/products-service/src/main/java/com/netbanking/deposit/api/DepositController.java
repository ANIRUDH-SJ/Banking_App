package com.netbanking.deposit.api;

import com.netbanking.deposit.service.DepositService;
import com.netbanking.security.SecurityContextHelper;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {
    private final DepositService deposits;

    public DepositController(DepositService deposits) {
        this.deposits = deposits;
    }

    @PostMapping("/quotes")
    public DepositQuoteResponse quote(@Valid @RequestBody DepositQuoteRequest request) {
        return deposits.quote(SecurityContextHelper.currentUserId(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse open(@Valid @RequestBody OpenDepositRequest request) {
        return deposits.open(SecurityContextHelper.currentUserId(), request);
    }

    @GetMapping
    public List<DepositResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return deposits.list(SecurityContextHelper.currentUserId(), page, size);
    }

    @GetMapping("/{depositId}")
    public DepositResponse get(@PathVariable String depositId) {
        return deposits.get(SecurityContextHelper.currentUserId(), depositId);
    }

    @PostMapping("/{depositId}/installments")
    public DepositResponse payDueInstallment(@PathVariable String depositId) {
        return deposits.payDueInstallment(SecurityContextHelper.currentUserId(), depositId);
    }
}
