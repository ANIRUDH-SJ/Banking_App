package com.netbanking.payment.service;

import static org.mockito.Mockito.verify;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.UnauthorizedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentAuditServiceTest {
    @Mock private AuditLogService auditLogService;

    @Test
    void recordsTransactionReferenceForCompletedPayments() {
        new PaymentAuditService(auditLogService).recordSuccess(7L, 10L, "FUND_TRANSFER", "TXN-123");

        verify(auditLogService)
                .record(
                        7L,
                        "FUND_TRANSFER_COMPLETED",
                        "ACCOUNT",
                        "10",
                        "SUCCESS",
                        "transactionReference=TXN-123");
    }

    @Test
    void recordsRejectedAuthorizationWithoutSecrets() {
        new PaymentAuditService(auditLogService)
                .recordRejected(
                        7L, 10L, "BILL_PAYMENT", new UnauthorizedException("OTP code is invalid."));

        verify(auditLogService)
                .record(
                        7L,
                        "BILL_PAYMENT_REJECTED",
                        "ACCOUNT",
                        "10",
                        "DENIED",
                        "reason=UnauthorizedException");
    }
}
