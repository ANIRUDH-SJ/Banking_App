package com.netbanking.customer.api;

import com.netbanking.customer.service.CustomerService;
import com.netbanking.security.SecurityContextHelper;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public CustomerProfileResponse profile() {
        return customers.getProfileForUser(SecurityContextHelper.currentUserId());
    }
}
