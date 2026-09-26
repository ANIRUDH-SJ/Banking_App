package com.netbanking.audit.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(nullable = false)
    private String outcome;

    @Lob
    @Column(name = "event_details")
    private String eventDetails;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    protected AuditEvent() {}

    public AuditEvent(
            Long userId, String eventType, String entityType, String entityId, String outcome) {
        this.userId = userId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.outcome = outcome;
    }

    public AuditEvent(
            Long userId,
            String eventType,
            String entityType,
            String entityId,
            String outcome,
            String eventDetails) {
        this(userId, eventType, entityType, entityId, outcome);
        this.eventDetails = eventDetails;
    }

    @PrePersist
    void setOccurredAt() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOutcome() {
        return outcome;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
