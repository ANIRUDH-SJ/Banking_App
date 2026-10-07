package com.netbanking.audit.api;

import com.netbanking.audit.service.AuditEventHandler;
import com.netbanking.events.EventEnvelope;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('SERVICE')")
public class AuditEventReceiver {
    private final AuditEventHandler handler;

    public AuditEventReceiver(AuditEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source()) || !"AUDIT".equals(event.type()))
            throw new SecurityException("Invalid audit event source or type.");
        handler.accept(event);
    }
}
