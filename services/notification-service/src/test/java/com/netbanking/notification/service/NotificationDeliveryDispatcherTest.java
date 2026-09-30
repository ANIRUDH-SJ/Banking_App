package com.netbanking.notification.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.notification.domain.NotificationChannel;
import com.netbanking.notification.service.NotificationDeliveryService.ClaimedDelivery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryDispatcherTest {
    @Mock private NotificationDeliveryService deliveries;
    @Mock private EmailService email;
    @Mock private SmsService sms;

    @Test
    void sendsClaimedEmailAndMarksItDelivered() {
        var delivery =
                new ClaimedDelivery(
                        3L,
                        NotificationChannel.EMAIL,
                        "asha@example.com",
                        "New sign-in",
                        "A sign-in was recorded.");
        when(deliveries.claimReady(50)).thenReturn(List.of(delivery));

        new NotificationDeliveryDispatcher(deliveries, email, sms).dispatch();

        verify(email)
                .send(
                        "asha@example.com",
                        "New sign-in",
                        "A sign-in was recorded.");
        verify(deliveries).markSent(3L);
    }

    @Test
    void recordsAdapterFailureForRetry() {
        var delivery =
                new ClaimedDelivery(
                        4L,
                        NotificationChannel.SMS,
                        "9999999999",
                        "Payment completed",
                        "Payment completed.");
        RuntimeException failure = new IllegalStateException("provider unavailable");
        when(deliveries.claimReady(50)).thenReturn(List.of(delivery));
        org.mockito.Mockito.doThrow(failure)
                .when(sms)
                .send("9999999999", "Payment completed.");

        new NotificationDeliveryDispatcher(deliveries, email, sms).dispatch();

        verify(deliveries).markFailed(4L, failure);
    }
}
