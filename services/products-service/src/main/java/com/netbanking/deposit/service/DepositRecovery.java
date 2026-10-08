package com.netbanking.deposit.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositRecovery {
    private final DepositService deposits;
    private final DepositClosureService closures;

    public DepositRecovery(DepositService deposits, DepositClosureService closures) {
        this.deposits = deposits;
        this.closures = closures;
    }

    @Scheduled(fixedDelayString = "${app.deposits.recovery-ms:60000}")
    public void recover() {
        deposits.recover();
        closures.recover();
    }
}
