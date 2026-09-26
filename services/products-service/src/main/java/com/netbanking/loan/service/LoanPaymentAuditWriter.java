package com.netbanking.loan.service;

import com.netbanking.audit.service.AuditLogService;

import org.springframework.stereotype.Component;

@Component
public class LoanPaymentAuditWriter {
    private final AuditLogService audit;

    public LoanPaymentAuditWriter(AuditLogService audit) {
        this.audit = audit;
    }

    public void recordCompleted(Long userId, Long loanId, String reference) {
        audit.record(
                userId,
                "LOAN_PAYMENT_COMPLETED",
                "LOAN",
                loanId.toString(),
                "SUCCESS",
                "transactionReference=" + reference);
    }
}
