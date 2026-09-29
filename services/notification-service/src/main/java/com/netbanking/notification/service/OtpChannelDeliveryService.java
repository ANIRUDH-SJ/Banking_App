package com.netbanking.notification.service;

import com.netbanking.discovery.OtpDeliveryClient;

import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class OtpChannelDeliveryService {
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final EmailService email;
    private final SmsService sms;

    public OtpChannelDeliveryService(EmailService email, SmsService sms) {
        this.email = email;
        this.sms = sms;
    }

    public OtpDeliveryClient.Receipt deliver(OtpDeliveryClient.Command command) {
        List<String> channels = new ArrayList<>();
        String expiry = EXPIRY_FORMAT.format(command.expiresAt());
        String message =
                "Your banking verification code is "
                        + command.code()
                        + ". It expires at "
                        + expiry
                        + ". Do not share this code.";
        email.send(command.email(), "Your banking verification code", message);
        channels.add("EMAIL");
        if (command.mobileNumber() != null && !command.mobileNumber().isBlank()) {
            sms.send(command.mobileNumber(), message);
            channels.add("SMS");
        }
        return new OtpDeliveryClient.Receipt(command.challengeId(), List.copyOf(channels));
    }
}
