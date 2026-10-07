package com.netbanking.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventTopics {
    private final String customer;
    private final String notifications;
    private final String audit;

    public EventTopics(
            @Value("${app.events.kafka.topics.customer:banking.customer-events.v1}")
                    String customer,
            @Value("${app.events.kafka.topics.notifications:banking.notification-commands.v1}")
                    String notifications,
            @Value("${app.events.kafka.topics.audit:banking.audit-events.v1}") String audit) {
        this.customer = customer;
        this.notifications = notifications;
        this.audit = audit;
    }

    public String forEvent(String destination, String type) {
        if ("accounts-ledger-service".equals(destination)
                && "CUSTOMER_REGISTERED".equals(type)) return customer;
        if ("notification-service".equals(destination) && "NOTIFICATION".equals(type))
            return notifications;
        if ("audit-reporting-service".equals(destination) && "AUDIT".equals(type)) return audit;
        throw new IllegalArgumentException(
                "No Kafka topic is configured for destination " + destination + " and type " + type);
    }

    public String customer() {
        return customer;
    }

    public String notifications() {
        return notifications;
    }

    public String audit() {
        return audit;
    }
}
