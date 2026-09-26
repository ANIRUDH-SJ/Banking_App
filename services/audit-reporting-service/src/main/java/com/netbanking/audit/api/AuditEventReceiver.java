package com.netbanking.audit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;
import com.netbanking.audit.service.AuditLogService.AuditPayload;
import com.netbanking.events.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('SERVICE')")
public class AuditEventReceiver {
    private final EventInbox inbox;
    private final ObjectMapper json;
    private final AuditEventRepository repository;

    public AuditEventReceiver(
            EventInbox inbox, ObjectMapper json, AuditEventRepository repository) {
        this.inbox = inbox;
        this.json = json;
        this.repository = repository;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source()) || !"AUDIT".equals(event.type()))
            throw new SecurityException("Invalid audit event source or type.");
        inbox.accept(
                event,
                payload -> {
                    var p = json.convertValue(payload, AuditPayload.class);
                    repository.saveAndFlush(
                            new AuditEvent(
                                    p.userId(),
                                    p.type(),
                                    p.entityType(),
                                    p.entityId(),
                                    p.outcome(),
                                    p.details()));
                });
    }
}
