package com.netbanking.notification.service;

public interface EmailService {
    void send(String recipient, String subject, String body);

    void send(String recipient, String subject, String body, Attachment attachment);

    record Attachment(String filename, String contentType, byte[] content) {}
}
