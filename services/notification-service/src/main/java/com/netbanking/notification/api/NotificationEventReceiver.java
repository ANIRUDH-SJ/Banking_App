package com.netbanking.notification.api;

import com.netbanking.events.EventEnvelope;
import com.netbanking.notification.service.NotificationEventHandler;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize(
        "hasAnyAuthority('SERVICE_payments-service','SERVICE_products-service','SERVICE_identity-service','SERVICE_accounts-ledger-service')")
public class NotificationEventReceiver {
    private final NotificationEventHandler handler;

    public NotificationEventReceiver(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source()) || !"NOTIFICATION".equals(event.type()))
            throw new SecurityException("Invalid notification event source or type.");
        handler.accept(event);
    }
}
