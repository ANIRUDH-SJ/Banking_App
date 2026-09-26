package com.netbanking.notification.service;

public interface SmsService {
    void send(String recipient, String message);
}
