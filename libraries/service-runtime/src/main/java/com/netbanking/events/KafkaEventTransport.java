package com.netbanking.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.events.transport", havingValue = "kafka")
public class KafkaEventTransport implements EventTransport {
    private final KafkaTemplate<String, EventEnvelope> kafka;
    private final ObjectMapper json;
    private final EventTopics topics;
    private final Duration sendTimeout;

    public KafkaEventTransport(
            KafkaTemplate<String, EventEnvelope> kafka,
            ObjectMapper json,
            EventTopics topics,
            @Value("${app.events.kafka.send-timeout:10s}") Duration sendTimeout) {
        this.kafka = kafka;
        this.json = json;
        this.topics = topics;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void send(String destination, String serializedEnvelope) throws Exception {
        EventEnvelope envelope = json.readValue(serializedEnvelope, EventEnvelope.class);
        kafka.send(topics.forEvent(destination, envelope.type()), key(envelope), envelope)
                .get(sendTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    static String key(EventEnvelope event) {
        for (String field : new String[] {"userId", "customerId", "entityId"}) {
            if (event.payload().hasNonNull(field)) return event.payload().get(field).asText();
        }
        return event.eventId();
    }
}
