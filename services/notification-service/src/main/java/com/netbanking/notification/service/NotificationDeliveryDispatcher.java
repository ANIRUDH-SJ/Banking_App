package com.netbanking.notification.service;

import com.netbanking.notification.domain.NotificationChannel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.notifications.delivery.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationDeliveryDispatcher {
    private final NotificationDeliveryService deliveries;
    private final EmailService email;
    private final SmsService sms;

    public NotificationDeliveryDispatcher(
            NotificationDeliveryService deliveries, EmailService email, SmsService sms) {
        this.deliveries = deliveries;
        this.email = email;
        this.sms = sms;
    }

    @Scheduled(fixedDelayString = "${app.notifications.delivery.interval-ms:5000}")
    public void dispatch() {
        for (var delivery : deliveries.claimReady(50)) {
            try {
                send(delivery);
                deliveries.markSent(delivery.deliveryId());
            } catch (RuntimeException failure) {
                deliveries.markFailed(delivery.deliveryId(), failure);
            }
        }
    }

    private void send(NotificationDeliveryService.ClaimedDelivery delivery) {
        if (delivery.channel() == NotificationChannel.EMAIL) {
            email.send(delivery.recipient(), delivery.subject(), delivery.message());
            return;
        }
        if (delivery.channel() == NotificationChannel.SMS) {
            sms.send(delivery.recipient(), delivery.message());
            return;
        }
        throw new IllegalStateException("Only external notification channels are dispatched.");
    }
}
