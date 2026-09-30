package com.netbanking.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_delivery_id")
    private Long notificationDeliveryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "failure_reason")
    private String failureReason;

    protected NotificationDelivery() {}

    public static NotificationDelivery deliveredInApp(Notification notification) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.notification = notification;
        delivery.channel = NotificationChannel.IN_APP;
        delivery.deliveryStatus = DeliveryStatus.SENT;
        delivery.nextAttemptAt = LocalDateTime.now();
        delivery.deliveredAt = LocalDateTime.now();
        return delivery;
    }

    public static NotificationDelivery pending(
            Notification notification, NotificationChannel channel, String recipient) {
        if (channel == NotificationChannel.IN_APP) {
            throw new IllegalArgumentException("In-app delivery is recorded as immediately sent.");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("A delivery recipient is required.");
        }
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.notification = notification;
        delivery.channel = channel;
        delivery.recipient = recipient.strip();
        delivery.deliveryStatus = DeliveryStatus.PENDING;
        delivery.nextAttemptAt = LocalDateTime.now();
        return delivery;
    }

    public Long getNotificationDeliveryId() {
        return notificationDeliveryId;
    }

    public Notification getNotification() {
        return notification;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void claim(LocalDateTime now) {
        deliveryStatus = DeliveryStatus.PROCESSING;
        attemptCount++;
        attemptedAt = now;
        failureReason = null;
    }

    public void markSent(LocalDateTime now) {
        deliveryStatus = DeliveryStatus.SENT;
        deliveredAt = now;
        nextAttemptAt = now;
        failureReason = null;
    }

    public void markFailed(String reason, LocalDateTime nextAttemptAt) {
        deliveryStatus = DeliveryStatus.FAILED;
        failureReason = truncate(reason, 500);
        this.nextAttemptAt = nextAttemptAt;
    }

    private static String truncate(String value, int maximum) {
        if (value == null || value.isBlank()) return "Delivery adapter failed.";
        String stripped = value.strip();
        return stripped.length() <= maximum ? stripped : stripped.substring(0, maximum);
    }
}
