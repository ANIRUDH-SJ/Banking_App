package com.netbanking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.notifications.email.provider",
        havingValue = "log",
        matchIfMissing = true)
public class DevelopmentEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailService.class);

    public void send(String recipient, String subject, String body) {
        log.info("Mock email delivered to {} with subject {}", mask(recipient), subject);
    }

    private static String mask(String recipient) {
        int separator = recipient.indexOf('@');
        return separator < 0 ? "***" : "***" + recipient.substring(separator);
    }
}
