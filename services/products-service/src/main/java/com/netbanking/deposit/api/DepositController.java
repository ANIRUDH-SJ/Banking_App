package com.netbanking.deposit.api;

import com.netbanking.deposit.service.DepositService;
import com.netbanking.deposit.service.DepositClosureService;
import com.netbanking.security.SecurityContextHelper;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {
    private final DepositService deposits;
    private final DepositClosureService closures;

    public DepositController(DepositService deposits, DepositClosureService closures) {
        this.deposits = deposits;
        this.closures = closures;
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

    @PostMapping("/{depositId}/closure-quotes")
    public DepositClosureQuoteResponse closureQuote(@PathVariable String depositId) {
        return closures.quote(SecurityContextHelper.currentUserId(), depositId);
    }

    @PostMapping("/{depositId}/closure-challenges")
    public DepositClosureChallengeResponse closureChallenge(
            @PathVariable String depositId,
            @Valid @RequestBody DepositClosureChallengeRequest request) {
        return closures.challenge(SecurityContextHelper.currentUserId(), depositId, request);
    }

    @PostMapping("/{depositId}/close")
    public DepositClosureResponse close(
            @PathVariable String depositId, @Valid @RequestBody CloseDepositRequest request) {
        return closures.close(SecurityContextHelper.currentUserId(), depositId, request);
    }
}
