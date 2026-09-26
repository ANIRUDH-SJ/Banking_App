package com.netbanking.payment.service;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentRecovery {
    private final PaymentWorkflowStore store;
    private final PaymentService payments;

    public PaymentRecovery(PaymentWorkflowStore store, PaymentService payments) {
        this.store = store;
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${app.payments.recovery-ms:15000}")
    public void recover() {
        for (var operation : store.recoverable())
            try {
                payments.settle(operation);
            } catch (RuntimeException failure) {
                store.defer(operation.key());
                LoggerFactory.getLogger(getClass())
                        .warn("Payment {} remains under recovery", operation.key());
            }
    }
}
