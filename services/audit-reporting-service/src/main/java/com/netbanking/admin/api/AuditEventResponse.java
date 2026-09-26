package com.netbanking.admin.api;

import java.time.LocalDateTime;

public record AuditEventResponse(
        Long auditEventId,
        Long userId,
        String eventType,
        String outcome,
        LocalDateTime occurredAt) {}
