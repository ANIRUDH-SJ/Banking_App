package com.netbanking.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class EventOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String source;

    public EventOutbox(
            JdbcTemplate jdbc,
            ObjectMapper json,
            @Value("${spring.application.name}") String source) {
        this.jdbc = jdbc;
        this.json = json;
        this.source = source;
    }

    @Transactional
    public void publish(String destination, String type, Object payload) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update(
                    "INSERT INTO event_outbox (event_id, destination, envelope) VALUES (?, ?, ?)",
                    id,
                    destination,
                    json.writeValueAsString(
                            new EventEnvelope(id, source, type, json.valueToTree(payload))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Event serialization failed", e);
        }
    }
}
