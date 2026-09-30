package com.netbanking.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.notifications.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String senderAddress;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.notifications.email.from}") String senderAddress) {
        if (senderAddress == null || senderAddress.isBlank()) {
            throw new IllegalArgumentException("An SMTP sender address is required.");
        }
        this.mailSender = mailSender;
        this.senderAddress = senderAddress.strip();
    }

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
