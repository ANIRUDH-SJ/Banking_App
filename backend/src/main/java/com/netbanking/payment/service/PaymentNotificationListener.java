package com.netbanking.payment.service;

import com.netbanking.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentNotificationListener.class);
    private final NotificationService notificationService;

    public PaymentNotificationListener(NotificationService notificationService) { this.notificationService = notificationService; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCustomer(PaymentCompletedEvent event) {
        try {
            notificationService.createInApp(event.userId(), event.paymentType() + "_COMPLETED", "Payment completed",
                    event.paymentType() + " " + event.transactionReference() + " completed for " + event.amount() + " " + event.currencyCode() + ".");
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create post-commit payment notification for transaction {}", event.transactionReference(), exception);
        }
    }
}
