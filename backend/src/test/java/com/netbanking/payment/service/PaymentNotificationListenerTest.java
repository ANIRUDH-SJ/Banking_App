package com.netbanking.payment.service;

import static org.mockito.Mockito.verify;

import com.netbanking.notification.service.NotificationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationListenerTest {
    @Mock private NotificationService notificationService;

    @Test
    void createsAReceiptNotificationAfterReceivingTheCommittedEvent() {
        new PaymentNotificationListener(notificationService).notifyCustomer(
                new PaymentCompletedEvent(7L, "Bill payment", "TXN-123",
                        new BigDecimal("250.00"), "INR"));

        verify(notificationService).createInApp(7L, "PAYMENT", "Bill payment completed",
                "Bill payment of 250.00 INR completed. Reference: TXN-123");
    }
}
