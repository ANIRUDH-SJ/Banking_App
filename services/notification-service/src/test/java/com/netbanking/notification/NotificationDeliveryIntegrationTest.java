package com.netbanking.notification;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.ServiceTestBase;
import com.netbanking.events.*;
import com.netbanking.notification.repository.NotificationRepository;
import com.netbanking.notification.service.NotificationService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationDeliveryIntegrationTest extends ServiceTestBase {
    @Autowired EventInbox inbox;
    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void tables() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS event_inbox(source_service VARCHAR(50),event_id"
                        + " VARCHAR(36),PRIMARY KEY(source_service,event_id))");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS event_outbox(event_id VARCHAR(36) PRIMARY"
                        + " KEY,destination VARCHAR(50),envelope CLOB)");
        jdbc.update("DELETE FROM event_inbox");
        jdbc.update("DELETE FROM event_outbox");
        notifications.deleteAll();
    }

    @Test
    void acknowledgmentNotificationAndAuditCommitTogetherAndDeduplicate() {
        var event =
                new EventEnvelope(
                        "payment-event",
                        "payments-service",
                        "NOTIFICATION",
                        new ObjectMapper().createObjectNode());
        assertThatThrownBy(
                        () ->
                                inbox.accept(
                                        event,
                                        p -> {
                                            service.createInApp(
                                                    7L,
                                                    "PAYMENT",
                                                    "Completed",
                                                    "Transfer completed");
                                            throw new IllegalStateException("lost transaction");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(notifications.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_inbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isZero();
        inbox.accept(
                event, p -> service.createInApp(7L, "PAYMENT", "Completed", "Transfer completed"));
        inbox.accept(
                event,
                p -> {
                    throw new AssertionError("Duplicate event");
                });
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isEqualTo(1);
    }
}
