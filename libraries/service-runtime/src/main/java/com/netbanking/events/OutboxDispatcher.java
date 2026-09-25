package com.netbanking.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.discovery.ServiceHttpClient;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ServiceHttpClient client;

    public OutboxDispatcher(JdbcTemplate jdbc, ObjectMapper json, ServiceHttpClient client) {
        this.jdbc = jdbc;
        this.json = json;
        this.client = client;
    }

    @Scheduled(fixedDelayString = "${app.events.interval-ms:5000}")
    public void dispatch() {
        var rows =
                jdbc.query(
                        "SELECT event_id, destination, envelope, attempts FROM event_outbox WHERE"
                            + " delivered_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP ORDER"
                            + " BY created_at FETCH FIRST 50 ROWS ONLY",
                        (rs, n) ->
                                new Pending(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getInt(4)));
        for (var row : rows) {
            try {
                client.post(
                        row.destination(),
                        "/internal/events",
                        json.readValue(row.envelope(), EventEnvelope.class),
                        Void.class);
                jdbc.update(
                        "UPDATE event_outbox SET delivered_at = CURRENT_TIMESTAMP WHERE event_id ="
                                + " ?",
                        row.id());
            } catch (Exception failure) {
                jdbc.update(
                        "UPDATE event_outbox SET attempts = attempts + 1, next_attempt_at = ? WHERE"
                                + " event_id = ?",
                        Timestamp.from(
                                Instant.now()
                                        .plusSeconds(Math.min(300, 5L * (row.attempts() + 1)))),
                        row.id());
                LoggerFactory.getLogger(getClass())
                        .warn("Event {} remains queued for {}", row.id(), row.destination());
            }
        }
    }

    private record Pending(String id, String destination, String envelope, int attempts) {}
}
