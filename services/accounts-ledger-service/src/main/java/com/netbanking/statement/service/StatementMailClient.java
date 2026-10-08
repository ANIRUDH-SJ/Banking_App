package com.netbanking.statement.service;

import com.netbanking.discovery.ServiceHttpClient;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;

/** Hands a generated statement to the notification service, which owns the customer's email. */
@Component
public class StatementMailClient {
    private final ServiceHttpClient client;

    public StatementMailClient(ServiceHttpClient client) {
        this.client = client;
    }

    public Receipt send(Long userId, String subject, String body, String filename, byte[] pdf) {
        return client.post(
                "notification-service",
                "/internal/statement-emails",
                new Command(userId, subject, body, filename, Base64.getEncoder().encodeToString(pdf)),
                Receipt.class);
    }

    public record Command(
            Long userId, String subject, String body, String attachmentName, String attachmentBase64) {}

    /** {@code sentTo} is masked by the notification service. */
    public record Receipt(String status, String sentTo, Instant sentAt) {}
}
