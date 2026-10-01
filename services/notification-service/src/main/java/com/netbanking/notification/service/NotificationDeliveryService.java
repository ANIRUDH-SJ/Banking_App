package com.netbanking.notification.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.notification.domain.NotificationChannel;
import com.netbanking.notification.domain.NotificationDelivery;
import com.netbanking.notification.repository.NotificationDeliveryRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationDeliveryService {
    private final NotificationDeliveryRepository deliveries;
    private final AuditLogService audit;
    private final int maximumAttempts;
    private final long baseRetrySeconds;

    public NotificationDeliveryService(
            NotificationDeliveryRepository deliveries,
            AuditLogService audit,
            @Value("${app.notifications.delivery.max-attempts:5}") int maximumAttempts,
            @Value("${app.notifications.delivery.base-retry-seconds:30}")
                    long baseRetrySeconds) {
        if (maximumAttempts < 1 || baseRetrySeconds < 1) {
            throw new IllegalArgumentException("Notification retry settings must be positive.");
        }
        this.deliveries = deliveries;
        this.audit = audit;
        this.maximumAttempts = maximumAttempts;
        this.baseRetrySeconds = baseRetrySeconds;
    }

    @Transactional
    public List<ClaimedDelivery> claimReady(int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Delivery batch size must be between 1 and 100.");
        }
        LocalDateTime now = LocalDateTime.now();
        return deliveries
                .findReadyForUpdate(
                        now, now.minusMinutes(5), PageRequest.of(0, batchSize))
                .stream()
                .map(
                        delivery -> {
                            delivery.claim(now);
                            return new ClaimedDelivery(
                                    delivery.getNotificationDeliveryId(),
                                    delivery.getChannel(),
                                    delivery.getRecipient(),
                                    delivery.getNotification().getTitle(),
                                    delivery.getNotification().getMessage());
                        })
                .toList();
    }

    @Transactional
    public void markSent(Long deliveryId) {
        NotificationDelivery delivery = require(deliveryId);
        delivery.markSent(LocalDateTime.now());
        audit.record(
                delivery.getNotification().getUserId(),
                "NOTIFICATION_DELIVERED",
                "NOTIFICATION",
                String.valueOf(delivery.getNotification().getNotificationId()),
                "SUCCESS",
                "channel=" + delivery.getChannel());
    }

    @Transactional
    public void markFailed(Long deliveryId, RuntimeException failure) {
        NotificationDelivery delivery = require(deliveryId);
        boolean terminal = delivery.getAttemptCount() >= maximumAttempts;
        long multiplier = 1L << Math.min(delivery.getAttemptCount() - 1, 10);
        LocalDateTime nextAttempt =
                terminal
                        ? LocalDateTime.now().plusYears(100)
                        : LocalDateTime.now().plusSeconds(baseRetrySeconds * multiplier);
        delivery.markFailed(failure.getMessage(), nextAttempt);
        if (terminal) {
            audit.record(
                    delivery.getNotification().getUserId(),
                    "NOTIFICATION_DELIVERY_FAILED",
                    "NOTIFICATION",
                    String.valueOf(delivery.getNotification().getNotificationId()),
                    "FAILURE",
                    "channel=" + delivery.getChannel());
        }
    }

    private NotificationDelivery require(Long deliveryId) {
        return deliveries
                .findWithNotificationById(deliveryId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Notification delivery was not found."));
    }

    public record ClaimedDelivery(
            Long deliveryId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String message) {}
}
