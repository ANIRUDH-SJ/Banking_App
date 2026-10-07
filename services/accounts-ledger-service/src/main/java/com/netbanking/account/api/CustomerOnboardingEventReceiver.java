package com.netbanking.account.api;

import com.netbanking.events.EventEnvelope;
import com.netbanking.account.service.CustomerOnboardingEventHandler;

import jakarta.validation.Valid;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('SERVICE_identity-service')")
public class CustomerOnboardingEventReceiver {
    private final CustomerOnboardingEventHandler handler;

    public CustomerOnboardingEventReceiver(CustomerOnboardingEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source())
                || !"identity-service".equals(event.source())
                || !"CUSTOMER_REGISTERED".equals(event.type())) {
            throw new AccessDeniedException("Invalid customer onboarding event source or type.");
        }
        handler.accept(event);
    }
}
