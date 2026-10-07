package com.netbanking.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(name = "app.notifications.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String senderAddress;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.notifications.email.from}") String senderAddress,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authenticationRequired,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        if (senderAddress == null || senderAddress.isBlank()) {
            throw new IllegalArgumentException("An SMTP sender address is required.");
        }
        if (authenticationRequired
                && (username == null || username.isBlank() || password == null || password.isBlank())) {
            throw new IllegalArgumentException(
                    "SMTP authentication requires a username and password.");
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

    @Override
    public void send(String recipient, String subject, String body, Attachment attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(
                    attachment.filename(),
                    new ByteArrayResource(attachment.content()),
                    attachment.contentType());
            mailSender.send(message);
        } catch (MessagingException failure) {
            throw new IllegalStateException("The email could not be composed.", failure);
        }
    }
}
