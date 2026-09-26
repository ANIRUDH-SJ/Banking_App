package com.netbanking.loan.service;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanRepaymentRecovery {
    private final LoanRepaymentStore store;
    private final LoanPaymentService service;

    public LoanRepaymentRecovery(LoanRepaymentStore store, LoanPaymentService service) {
        this.store = store;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.recovery.interval-ms:15000}")
    public void recover() {
        for (var operation : store.recoverable()) {
            try {
                service.settle(operation);
            } catch (Exception failure) {
                store.defer(operation.key());
                LoggerFactory.getLogger(getClass())
                        .warn("Loan repayment {} remains unresolved", operation.key());
            }
        }
    }
}
