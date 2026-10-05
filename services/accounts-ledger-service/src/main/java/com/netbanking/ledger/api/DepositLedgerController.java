package com.netbanking.ledger.api;

import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.LedgerReceipt;
import com.netbanking.ledger.service.DepositLedgerService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ledger/deposits")
@PreAuthorize("hasAuthority('SERVICE_products-service')")
public class DepositLedgerController {
    private final DepositLedgerService ledger;

    public DepositLedgerController(DepositLedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    public LedgerReceipt post(@Valid @RequestBody DepositLedgerCommand command) {
        return ledger.post(command);
    }
}
