package com.netbanking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.notifications.email.provider",
        havingValue = "log",
        matchIfMissing = true)
public class DevelopmentEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailService.class);

    private final boolean logContent;

    public DevelopmentEmailService(
            @Value("${app.notifications.log-message-content:false}") boolean logContent) {
        this.logContent = logContent;
        log.warn("Email provider is log-only; messages are not sent to an inbox.");
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (logContent) {
            log.warn(
                    "DEVELOPMENT ONLY: email to {} with subject {}: {}",
                    mask(recipient),
                    subject,
                    body);
            return;
        }
        log.info("Mock email delivered to {} with subject {}", mask(recipient), subject);
    }

    @Override
    public void send(String recipient, String subject, String body, Attachment attachment) {
        log.info(
                "Mock email delivered to {} with subject {} and attachment {} ({} bytes)",
                mask(recipient),
                subject,
                attachment.filename(),
                attachment.content().length);
    }

    private static String mask(String recipient) {
        int separator = recipient.indexOf('@');
        return separator < 0 ? "***" : "***" + recipient.substring(separator);
    }
}
