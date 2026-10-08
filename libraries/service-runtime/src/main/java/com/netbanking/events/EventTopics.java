package com.netbanking.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventTopics {
    private final String events;

    public EventTopics(
            @Value("${app.events.kafka.topic:banking.events.v1}") String events) {
        this.events = events;
    }

    public String forEvent(String destination, String type) {
        if ("accounts-ledger-service".equals(destination)
                && "CUSTOMER_REGISTERED".equals(type)) return events;
        if ("notification-service".equals(destination) && "NOTIFICATION".equals(type))
            return events;
        if ("audit-reporting-service".equals(destination) && "AUDIT".equals(type)) return events;
        throw new IllegalArgumentException(
                "No Kafka topic is configured for destination " + destination + " and type " + type);
    }

    public String events() {
        return events;
    }
}
