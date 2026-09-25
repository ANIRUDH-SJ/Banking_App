package com.netbanking.events;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

@Service
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
public class EventInbox {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public EventInbox(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(manager);
    }

    public void accept(EventEnvelope event, Consumer<JsonNode> consumer) {
        try {
            transactions.executeWithoutResult(
                    tx -> {
                        jdbc.update(
                                "INSERT INTO event_inbox (source_service, event_id) VALUES (?, ?)",
                                event.source(),
                                event.eventId());
                        consumer.accept(event.payload());
                    });
        } catch (DuplicateKeyException duplicate) {
            Integer found =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM event_inbox WHERE source_service = ? AND event_id"
                                    + " = ?",
                            Integer.class,
                            event.source(),
                            event.eventId());
            if (found == null || found == 0) throw duplicate;
        }
    }
}
