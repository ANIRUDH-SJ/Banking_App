package com.netbanking.customer.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.discovery.NotificationRecipientDirectory.NotificationRecipient;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationRecipientService {
    private final AppUserRepository users;
    private final CustomerRepository customers;

    public NotificationRecipientService(
            AppUserRepository users, CustomerRepository customers) {
        this.users = users;
        this.customers = customers;
    }

    public NotificationRecipient requireForUser(Long userId) {
        AppUser user =
                users.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
        Customer customer =
                customers
                        .findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Customer profile was not found."));
        return new NotificationRecipient(user.getEmail(), customer.getMobileNumber());
    }
}
