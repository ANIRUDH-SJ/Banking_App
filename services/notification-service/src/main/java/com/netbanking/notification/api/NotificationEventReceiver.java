package com.netbanking.notification.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.events.*;
import com.netbanking.events.NotificationPublisher.Notice;
import com.netbanking.notification.service.NotificationService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize(
        "hasAnyAuthority('SERVICE_payments-service','SERVICE_products-service','SERVICE_identity-service','SERVICE_accounts-ledger-service')")
public class NotificationEventReceiver {
    private final EventInbox inbox;
    private final ObjectMapper json;
    private final NotificationService service;

    public NotificationEventReceiver(
            EventInbox inbox, ObjectMapper json, NotificationService service) {
        this.inbox = inbox;
        this.json = json;
        this.service = service;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source()) || !"NOTIFICATION".equals(event.type()))
            throw new SecurityException("Invalid notification event source or type.");
        inbox.accept(
                event,
                payload -> {
                    var p = json.convertValue(payload, Notice.class);
                    service.createInApp(p.userId(), "PAYMENT", p.title(), p.message());
                });
    }
}
