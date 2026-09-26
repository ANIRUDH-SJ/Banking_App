package com.netbanking.audit.service;

import com.netbanking.events.EventOutbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class AuditLogService {
    private final EventOutbox outbox;

    public AuditLogService(EventOutbox outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void record(
            Long userId, String type, String entityType, String entityId, String outcome) {
        record(userId, type, entityType, entityId, outcome, null);
    }

    @Transactional
    public void record(
            Long userId,
            String type,
            String entityType,
            String entityId,
            String outcome,
            String details) {
        outbox.publish(
                "audit-reporting-service",
                "AUDIT",
                new AuditPayload(userId, type, entityType, entityId, outcome, details));
    }

    public record AuditPayload(
            Long userId,
            String type,
            String entityType,
            String entityId,
            String outcome,
            String details) {}
}
