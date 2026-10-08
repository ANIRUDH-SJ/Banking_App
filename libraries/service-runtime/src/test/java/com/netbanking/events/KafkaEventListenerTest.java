package com.netbanking.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;

class KafkaEventListenerTest {
    @Test
    void aConsumerHandlesOnlyItsEventFamilyFromTheSharedTopic() {
        Validator validator = mock(Validator.class);
        var received = new ArrayList<EventEnvelope>();
        var listener = new KafkaEventListener(received::add, validator, "AUDIT");
        var json = new ObjectMapper();
        var notification =
                new EventEnvelope("event-1", "payments-service", "NOTIFICATION", json.createObjectNode());
        var audit =
                new EventEnvelope("event-2", "payments-service", "AUDIT", json.createObjectNode());
        when(validator.validate(notification)).thenReturn(Set.of());
        when(validator.validate(audit)).thenReturn(Set.of());

        listener.receive(notification);
        listener.receive(audit);

        assertThat(received).containsExactly(audit);
    }
}
