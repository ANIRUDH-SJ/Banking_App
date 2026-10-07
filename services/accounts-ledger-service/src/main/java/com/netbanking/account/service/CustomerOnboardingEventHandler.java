package com.netbanking.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.contracts.CustomerRegistered;
import com.netbanking.events.BankingEventHandler;
import com.netbanking.events.EventEnvelope;
import com.netbanking.events.EventInbox;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class CustomerOnboardingEventHandler implements BankingEventHandler {
    private final EventInbox inbox;
    private final ObjectMapper json;
    private final CustomerAccountProvisioningService provisioning;

    public CustomerOnboardingEventHandler(
            EventInbox inbox,
            ObjectMapper json,
            CustomerAccountProvisioningService provisioning) {
        this.inbox = inbox;
        this.json = json;
        this.provisioning = provisioning;
    }

    @Override
    public void accept(EventEnvelope event) {
        if (!"identity-service".equals(event.source())
                || !"CUSTOMER_REGISTERED".equals(event.type()))
            throw new AccessDeniedException("Invalid customer onboarding event source or type.");
        inbox.accept(
                event,
                payload ->
                        provisioning.provision(
                                json.convertValue(payload, CustomerRegistered.class)));
    }
}
