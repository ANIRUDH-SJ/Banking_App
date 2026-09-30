package com.netbanking.audit;

import com.netbanking.audit.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuditService {
    private final AuditLogService audit;

    public IdentityAuditService(AuditLogService audit) {
        this.audit = audit;
    }

    @Transactional
    public void success(Long userId, String type, String entityType, String entityId) {
        audit.record(userId, type, entityType, entityId, "SUCCESS");
    }

    @Transactional
    public void success(
            Long userId,
            String type,
            String entityType,
            String entityId,
            String details) {
        audit.record(userId, type, entityType, entityId, "SUCCESS", details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(Long userId, String type, String entityType, String entityId, String reason) {
        audit.record(userId, type, entityType, entityId, "DENIED", "reason=" + reason);
    }
}
