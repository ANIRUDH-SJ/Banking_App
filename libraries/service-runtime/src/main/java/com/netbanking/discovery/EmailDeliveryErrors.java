package com.netbanking.discovery;

import com.netbanking.common.exception.EmailDeliveryException;

/** Only known email-delivery contracts cross the notification/identity/payments boundaries. */
final class EmailDeliveryErrors {
    private EmailDeliveryErrors() {}

    static RuntimeException translate(DownstreamRejectedException rejected) {
        if (rejected.getStatusCode().value() == 422
                && "EMAIL_RECIPIENT_UNDELIVERABLE".equals(rejected.code())) {
            return EmailDeliveryException.undeliverableRecipient();
        }
        if (rejected.getStatusCode().value() == 503
                && "EMAIL_DELIVERY_UNAVAILABLE".equals(rejected.code())) {
            return EmailDeliveryException.unavailable();
        }
        return rejected;
    }
}
