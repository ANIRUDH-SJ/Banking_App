package com.netbanking.customer.api;

import com.netbanking.customer.service.CustomerService;
import com.netbanking.customer.service.NotificationRecipientService;
import com.netbanking.discovery.CustomerDirectory.CustomerIdentity;
import com.netbanking.discovery.NotificationRecipientDirectory.NotificationRecipient;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/customers")
@PreAuthorize(
        "hasAnyAuthority('SERVICE_accounts-ledger-service','SERVICE_payments-service','SERVICE_products-service','SERVICE_notification-service')")
public class InternalCustomerController {
    private final CustomerService customers;
    private final NotificationRecipientService recipients;

    public InternalCustomerController(
            CustomerService customers, NotificationRecipientService recipients) {
        this.customers = customers;
        this.recipients = recipients;
    }

    @GetMapping("/by-user/{userId}")
    public CustomerIdentity customer(@PathVariable Long userId) {
        return new CustomerIdentity(customers.requireCustomerIdForUser(userId));
    }

    @GetMapping("/by-user/{userId}/notification-recipient")
    public NotificationRecipient notificationRecipient(@PathVariable Long userId) {
        return recipients.requireForUser(userId);
    }
}
