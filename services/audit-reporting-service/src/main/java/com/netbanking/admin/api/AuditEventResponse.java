package com.netbanking.admin.api;

import java.time.LocalDateTime;

public record AuditEventResponse(
        Long auditEventId,
        Long userId,
        String eventType,
        String entityType,
        String entityId,
        String outcome,
        String details,
        LocalDateTime occurredAt) {}
