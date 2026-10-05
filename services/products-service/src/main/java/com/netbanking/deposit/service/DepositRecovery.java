package com.netbanking.deposit.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositRecovery {
    private final DepositService deposits;

    public DepositRecovery(DepositService deposits) { this.deposits = deposits; }

    @Scheduled(fixedDelayString = "${app.deposits.recovery-ms:60000}")
    public void recover() { deposits.recover(); }
}
