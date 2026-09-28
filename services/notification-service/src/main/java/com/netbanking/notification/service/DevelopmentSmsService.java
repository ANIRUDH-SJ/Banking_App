package com.netbanking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.notifications.sms.provider",
        havingValue = "log",
        matchIfMissing = true)
public class DevelopmentSmsService implements SmsService {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentSmsService.class);

    public void send(String recipient, String message) {
        String suffix = recipient.length() <= 4 ? recipient : recipient.substring(recipient.length() - 4);
        log.info("Mock SMS delivered to ***{}", suffix);
    }
}
