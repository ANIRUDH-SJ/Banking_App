package com.netbanking.notification.service;

import com.netbanking.discovery.NotificationRecipientDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;

/** Emails a customer-requested statement to the address registered with the bank. */
@Service
public class StatementEmailService {
    private static final Logger log = LoggerFactory.getLogger(StatementEmailService.class);
    private static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    private final NotificationRecipientDirectory recipients;
    private final EmailService email;
    private final NotificationService notifications;

    public StatementEmailService(
            NotificationRecipientDirectory recipients,
            EmailService email,
            NotificationService notifications) {
        this.recipients = recipients;
        this.email = email;
        this.notifications = notifications;
    }

    public Receipt send(Command command) {
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(command.attachmentBase64());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("The statement attachment is not valid.");
        }
        if (pdf.length == 0 || pdf.length > MAX_ATTACHMENT_BYTES)
            throw new IllegalArgumentException("The statement attachment size is not supported.");
        if (!command.attachmentName().matches("[A-Za-z0-9._-]{1,80}\\.pdf"))
            throw new IllegalArgumentException("The statement file name is not valid.");
        String address = recipients.requireForUser(command.userId()).email();
        if (address == null || address.isBlank())
            throw new IllegalStateException("No email address is registered for this customer.");
        try {
            email.send(
                    address,
                    command.subject(),
                    command.body(),
                    new EmailService.Attachment(command.attachmentName(), "application/pdf", pdf));
        } catch (RuntimeException failure) {
            log.warn("Statement email failed for user {}: {}", command.userId(), failure.getClass().getSimpleName());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The statement could not be emailed. Try again shortly.");
        }
        String masked = mask(address);
        notifications.createInApp(
                command.userId(),
                "STATEMENT_EMAILED",
                "Statement emailed",
                "We sent " + command.attachmentName() + " to " + masked + ". "
                        + "If you did not request it, review your recent activity.");
        return new Receipt("SENT", masked, Instant.now());
    }

    static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 0) return "***";
        return address.charAt(0) + "***" + address.substring(at);
    }

    public record Command(
            Long userId, String subject, String body, String attachmentName, String attachmentBase64) {}

    public record Receipt(String status, String sentTo, Instant sentAt) {}
}
