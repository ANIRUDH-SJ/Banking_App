package com.netbanking.payment.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.UnauthorizedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAuditService {
    private final AuditLogService auditLogService;

    public PaymentAuditService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void recordSuccess(Long userId, Long sourceAccountId, String operation,
                              String transactionReference) {
        auditLogService.record(userId, operation + "_COMPLETED", "ACCOUNT",
                String.valueOf(sourceAccountId), "SUCCESS",
                "transactionReference=" + transactionReference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(Long userId, Long sourceAccountId, String operation,
                               RuntimeException failure) {
        String outcome = isDenied(failure) ? "DENIED" : "FAILURE";
        auditLogService.record(userId, operation + "_REJECTED", "ACCOUNT",
                sourceAccountId == null ? null : String.valueOf(sourceAccountId), outcome,
                "reason=" + failure.getClass().getSimpleName());
    }

    private static boolean isDenied(RuntimeException failure) {
        return failure instanceof UnauthorizedException
                || failure instanceof AccessDeniedException
                || failure instanceof SecurityException
                || failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException
                || failure instanceof ConflictException;
    }
}
