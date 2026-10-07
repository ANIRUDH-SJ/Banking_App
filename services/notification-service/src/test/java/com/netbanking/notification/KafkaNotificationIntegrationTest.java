package com.netbanking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.ServiceTestBase;
import com.netbanking.discovery.NotificationRecipientDirectory;
import com.netbanking.discovery.NotificationRecipientDirectory.NotificationRecipient;
import com.netbanking.events.EventEnvelope;
import com.netbanking.events.NotificationPublisher.Notice;
import com.netbanking.notification.repository.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "KAFKA_INTEGRATION_TEST", matches = "true")
class KafkaNotificationIntegrationTest extends ServiceTestBase {
    @Autowired KafkaTemplate<Object, Object> kafka;
    @Autowired ObjectMapper json;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean NotificationRecipientDirectory recipients;

    @Value("${app.events.kafka.consumer-topic}")
    String topic;

    @BeforeEach
    void prepareInbox() {
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
    void consumesNotificationThroughRealKafkaBroker() throws Exception {
        long userId = 88001L;
        String title = "Kafka integration " + UUID.randomUUID();
        when(recipients.requireForUser(userId))
                .thenReturn(new NotificationRecipient("kafka@example.test", "+919999999999"));

        var payload = json.valueToTree(new Notice(userId, "SYSTEM", title, "Broker delivery verified"));
        var event =
                new EventEnvelope(
                        UUID.randomUUID().toString(),
                        "payments-service",
                        "NOTIFICATION",
                        payload);

        kafka.send(topic, Long.toString(userId), event).get();

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(notifications.findAll())
                                        .anySatisfy(
                                                notification ->
                                                        assertThat(notification)
                                                                .satisfies(
                                                                        saved -> {
                                                                            assertThat(saved.getUserId())
                                                                                    .isEqualTo(userId);
                                                                            assertThat(saved.getTitle())
                                                                                    .isEqualTo(title);
                                                                        })));
    }
}
