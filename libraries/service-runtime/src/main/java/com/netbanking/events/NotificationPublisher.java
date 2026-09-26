package com.netbanking.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class NotificationPublisher {
    private final EventOutbox outbox;

    public NotificationPublisher(EventOutbox outbox) {
        this.outbox = outbox;
    }

    public void publish(Long userId, String title, String message) {
        outbox.publish("notification-service", "NOTIFICATION", new Notice(userId, title, message));
    }

    public record Notice(Long userId, String title, String message) {}
}
