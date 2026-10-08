package com.netbanking.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists exhausted Kafka records in the consuming service's Oracle schema. */
@Component
@ConditionalOnExpression(
        "'${app.events.transport:http}' == 'kafka' and '${app.events.kafka.consumer-topic:}' != ''")
public class KafkaEventFailureStore {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventFailureStore.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public KafkaEventFailureStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(ConsumerRecord<?, ?> record, Exception failure) {
        String payload;
        try {
            payload = json.writeValueAsString(record.value());
        } catch (JsonProcessingException serializationFailure) {
            payload = String.valueOf(record.value());
        }
        try {
            jdbc.update(
                    "INSERT INTO event_consumer_failure "
                            + "(topic_name, event_partition, event_offset, event_key, payload, failure_reason) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    truncate(String.valueOf(record.key()), 200),
                    payload,
                    truncate(failure.toString(), 1000));
        } catch (DuplicateKeyException duplicate) {
            log.info(
                    "Kafka failure {}-{}-{} was already recorded.",
                    record.topic(),
                    record.partition(),
                    record.offset());
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
