package com.netbanking.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;
import com.netbanking.audit.service.AuditLogService.AuditPayload;
import com.netbanking.events.BankingEventHandler;
import com.netbanking.events.EventEnvelope;
import com.netbanking.events.EventInbox;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuditEventHandler implements BankingEventHandler {
    private static final Set<String> ALLOWED_SOURCES =
            Set.of(
                    "identity-service",
                    "accounts-ledger-service",
                    "payments-service",
                    "products-service",
                    "notification-service");

    private final EventInbox inbox;
    private final ObjectMapper json;
    private final AuditEventRepository repository;

    public AuditEventHandler(EventInbox inbox, ObjectMapper json, AuditEventRepository repository) {
        this.inbox = inbox;
        this.json = json;
        this.repository = repository;
    }

    @Override
    public void accept(EventEnvelope event) {
        if (!ALLOWED_SOURCES.contains(event.source()) || !"AUDIT".equals(event.type()))
            throw new SecurityException("Invalid audit event source or type.");
        inbox.accept(
                event,
                payload -> {
                    var audit = json.convertValue(payload, AuditPayload.class);
                    repository.saveAndFlush(
                            new AuditEvent(
                                    audit.userId(),
                                    audit.type(),
                                    audit.entityType(),
                                    audit.entityId(),
                                    audit.outcome(),
                                    audit.details()));
                });
    }
}
