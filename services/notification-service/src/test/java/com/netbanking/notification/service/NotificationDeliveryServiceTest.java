package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.notification.domain.DeliveryStatus;
import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.domain.NotificationChannel;
import com.netbanking.notification.domain.NotificationDelivery;
import com.netbanking.notification.repository.NotificationDeliveryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {
    @Mock private NotificationDeliveryRepository deliveries;
    @Mock private AuditLogService audit;

    @Test
    void claimsReadyDeliveriesBeforeDispatch() {
        Notification notification =
                new Notification(42L, "SECURITY", "New sign-in", "A sign-in was recorded.");
        NotificationDelivery delivery =
                NotificationDelivery.pending(
                        notification, NotificationChannel.EMAIL, "asha@example.com");
        when(deliveries.findReadyForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(delivery));

        var claimed = new NotificationDeliveryService(deliveries, audit, 3, 30).claimReady(25);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(claimed.get(0).recipient()).isEqualTo("asha@example.com");
        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.PROCESSING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getAttemptedAt()).isNotNull();
    }

    @Test
    void schedulesASecondAttemptAfterAnAdapterFailure() {
        Notification notification =
                new Notification(42L, "SECURITY", "New sign-in", "A sign-in was recorded.");
        NotificationDelivery delivery =
                NotificationDelivery.pending(notification, NotificationChannel.SMS, "9999999999");
        delivery.claim(LocalDateTime.now());
        when(deliveries.findWithNotificationById(7L)).thenReturn(Optional.of(delivery));

        LocalDateTime before = LocalDateTime.now();
        new NotificationDeliveryService(deliveries, audit, 3, 30)
                .markFailed(7L, new IllegalStateException("provider unavailable"));

        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getFailureReason()).isEqualTo("provider unavailable");
        assertThat(delivery.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(30));
    }

    @Test
    void recordsTerminalDeliveryFailureAfterTheMaximumAttempts() {
        Notification notification =
                new Notification(42L, "SECURITY", "New sign-in", "A sign-in was recorded.");
        NotificationDelivery delivery =
                NotificationDelivery.pending(notification, NotificationChannel.SMS, "9999999999");
        delivery.claim(LocalDateTime.now());
        delivery.claim(LocalDateTime.now());
        when(deliveries.findWithNotificationById(8L)).thenReturn(Optional.of(delivery));

        new NotificationDeliveryService(deliveries, audit, 2, 30)
                .markFailed(8L, new IllegalStateException("provider unavailable"));

        assertThat(delivery.getNextAttemptAt()).isAfter(LocalDateTime.now().plusYears(99));
        verify(audit)
                .record(
                        eq(42L),
                        eq("NOTIFICATION_DELIVERY_FAILED"),
                        eq("NOTIFICATION"),
                        eq("null"),
                        eq("FAILURE"),
                        eq("channel=SMS"));
    }
}
