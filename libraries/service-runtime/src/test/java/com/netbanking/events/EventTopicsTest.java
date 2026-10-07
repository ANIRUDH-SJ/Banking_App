package com.netbanking.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class EventTopicsTest {
    private final EventTopics topics =
            new EventTopics("customer-topic", "notification-topic", "audit-topic");

    @Test
    void routesTheThreeSupportedEventFamilies() {
        assertThat(topics.forEvent("accounts-ledger-service", "CUSTOMER_REGISTERED"))
                .isEqualTo("customer-topic");
        assertThat(topics.forEvent("notification-service", "NOTIFICATION"))
                .isEqualTo("notification-topic");
        assertThat(topics.forEvent("audit-reporting-service", "AUDIT"))
                .isEqualTo("audit-topic");
    }

    @Test
    void rejectsAnUnknownDestinationAndTypeInsteadOfMisroutingIt() {
        assertThatThrownBy(() -> topics.forEvent("unknown-service", "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No Kafka topic");
        assertThatThrownBy(() -> topics.forEvent("unknown-service", "CUSTOMER_REGISTERED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No Kafka topic");
    }

    @Test
    void usesBusinessIdentityAsPartitionKeyAndFallsBackToEventId() {
        var json = new ObjectMapper();
        assertThat(
                        KafkaEventTransport.key(
                                new EventEnvelope(
                                        "event-1",
                                        "payments-service",
                                        "NOTIFICATION",
                                        json.createObjectNode().put("userId", 42))))
                .isEqualTo("42");
        assertThat(
                        KafkaEventTransport.key(
                                new EventEnvelope(
                                        "event-2",
                                        "payments-service",
                                        "AUDIT",
                                        json.createObjectNode())))
                .isEqualTo("event-2");
    }
}
