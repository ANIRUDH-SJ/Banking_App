package com.netbanking.events;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EventInboxTest {
    @Test
    void deduplicatesCommittedEventsButRetriesRolledBackConsumers() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:inbox;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE event_inbox(source_service VARCHAR(50),event_id VARCHAR(36),PRIMARY"
                        + " KEY(source_service,event_id))");
        jdbc.execute("CREATE TABLE deliveries(id INT PRIMARY KEY)");
        var inbox = new EventInbox(jdbc, new DataSourceTransactionManager(source));
        var event =
                new EventEnvelope(
                        "event-1",
                        "payments-service",
                        "NOTIFICATION",
                        new ObjectMapper().createObjectNode());
        assertThatThrownBy(
                        () ->
                                inbox.accept(
                                        event,
                                        p -> {
                                            jdbc.update("INSERT INTO deliveries VALUES (1)");
                                            throw new IllegalStateException("consumer failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isZero();
        inbox.accept(event, p -> jdbc.update("INSERT INTO deliveries VALUES (1)"));
        inbox.accept(
                event,
                p -> {
                    throw new AssertionError("duplicate delivered");
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class))
                .isEqualTo(1);
    }
}
