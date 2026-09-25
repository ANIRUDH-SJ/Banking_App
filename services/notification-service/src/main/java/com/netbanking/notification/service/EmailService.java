package com.netbanking.notification.service;

public interface EmailService {
    void send(String recipient, String subject, String body);
}
