package com.netbanking.payment.service;

import com.netbanking.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);
    private final NotificationService notificationService;

    public PaymentNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCustomer(PaymentCompletedEvent event) {
        try {
            notificationService.createInApp(event.userId(), "PAYMENT", event.title() + " completed",
                    event.title() + " of " + event.amount().toPlainString() + " " + event.currencyCode()
                            + " completed. Reference: " + event.transactionReference());
        } catch (RuntimeException exception) {
            log.error("Unable to create the post-commit payment notification for user {} and reference {}.",
                    event.userId(), event.transactionReference(), exception);
        }
    }
}
