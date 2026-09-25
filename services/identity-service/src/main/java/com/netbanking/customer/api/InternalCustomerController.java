package com.netbanking.customer.api;

import com.netbanking.customer.service.CustomerService;
import com.netbanking.discovery.CustomerDirectory.CustomerIdentity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/customers")
@PreAuthorize(
        "hasAnyAuthority('SERVICE_accounts-ledger-service','SERVICE_payments-service','SERVICE_products-service')")
public class InternalCustomerController {
    private final CustomerService customers;

    public InternalCustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/by-user/{userId}")
    public CustomerIdentity customer(@PathVariable Long userId) {
        return new CustomerIdentity(customers.requireCustomerIdForUser(userId));
    }
}
