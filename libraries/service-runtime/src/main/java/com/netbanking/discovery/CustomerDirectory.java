package com.netbanking.discovery;

import org.springframework.stereotype.Service;

@Service
public class CustomerDirectory {
    private final ServiceHttpClient client;

    public CustomerDirectory(ServiceHttpClient client) {
        this.client = client;
    }

    public Long requireCustomerIdForUser(Long userId) {
        return client.get(
                        "identity-service",
                        "/internal/customers/by-user/" + userId,
                        CustomerIdentity.class)
                .customerId();
    }

    public record CustomerIdentity(Long customerId) {}
}
