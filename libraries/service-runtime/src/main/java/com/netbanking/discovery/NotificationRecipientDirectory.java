package com.netbanking.discovery;

import org.springframework.stereotype.Service;

@Service
public class NotificationRecipientDirectory {
    private final ServiceHttpClient client;

    public NotificationRecipientDirectory(ServiceHttpClient client) {
        this.client = client;
    }

    public NotificationRecipient requireForUser(Long userId) {
        return client.get(
                "identity-service",
                "/internal/customers/by-user/" + userId + "/notification-recipient",
                NotificationRecipient.class);
    }

    public record NotificationRecipient(String email, String mobileNumber) {}
}
