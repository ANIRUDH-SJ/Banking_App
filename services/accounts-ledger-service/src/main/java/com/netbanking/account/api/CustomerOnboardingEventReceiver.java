package com.netbanking.account.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.account.service.CustomerAccountProvisioningService;
import com.netbanking.contracts.CustomerRegistered;
import com.netbanking.events.EventEnvelope;
import com.netbanking.events.EventInbox;

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
    private final EventInbox inbox;
    private final ObjectMapper json;
    private final CustomerAccountProvisioningService provisioning;

    public CustomerOnboardingEventReceiver(
            EventInbox inbox,
            ObjectMapper json,
            CustomerAccountProvisioningService provisioning) {
        this.inbox = inbox;
        this.json = json;
        this.provisioning = provisioning;
    }

    @PostMapping("/internal/events")
    public void accept(@Valid @RequestBody EventEnvelope event, Authentication caller) {
        if (!caller.getName().equals(event.source())
                || !"identity-service".equals(event.source())
                || !"CUSTOMER_REGISTERED".equals(event.type())) {
            throw new AccessDeniedException("Invalid customer onboarding event source or type.");
        }
        inbox.accept(
                event,
                payload ->
                        provisioning.provision(
                                json.convertValue(payload, CustomerRegistered.class)));
    }
}
