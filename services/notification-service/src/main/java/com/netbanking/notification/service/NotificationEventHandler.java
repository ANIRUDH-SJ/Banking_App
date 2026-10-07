package com.netbanking.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.discovery.NotificationRecipientDirectory;
import com.netbanking.events.BankingEventHandler;
import com.netbanking.events.EventEnvelope;
import com.netbanking.events.EventInbox;
import com.netbanking.events.NotificationPublisher.Notice;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class NotificationEventHandler implements BankingEventHandler {
    private static final Set<String> ALLOWED_SOURCES =
            Set.of(
                    "identity-service",
                    "accounts-ledger-service",
                    "payments-service",
                    "products-service");

    private final EventInbox inbox;
    private final ObjectMapper json;
    private final NotificationService notifications;
    private final NotificationRecipientDirectory recipients;

    public NotificationEventHandler(
            EventInbox inbox,
            ObjectMapper json,
            NotificationService notifications,
            NotificationRecipientDirectory recipients) {
        this.inbox = inbox;
        this.json = json;
        this.notifications = notifications;
        this.recipients = recipients;
    }

    @Override
    public void accept(EventEnvelope event) {
        if (!ALLOWED_SOURCES.contains(event.source()) || !"NOTIFICATION".equals(event.type()))
            throw new SecurityException("Invalid notification event source or type.");
        inbox.accept(
                event,
                payload -> {
                    var notice = json.convertValue(payload, Notice.class);
                    notifications.create(
                            notice.userId(),
                            notice.type(),
                            notice.title(),
                            notice.message(),
                            recipients.requireForUser(notice.userId()));
                });
    }
}
