package com.netbanking.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class DevelopmentEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailService.class);

    public void send(String recipient, String subject, String body) {
        log.info("Development email queued for {} with subject {}", recipient, subject);
    }
}
