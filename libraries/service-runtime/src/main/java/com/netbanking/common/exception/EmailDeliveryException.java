package com.netbanking.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Public, fixed messages; SMTP responses and credentials must never be relayed. */
public class EmailDeliveryException extends ResponseStatusException {
    private final String code;

    private EmailDeliveryException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String code() { return code; }

    public static EmailDeliveryException undeliverableRecipient() {
        return new EmailDeliveryException(HttpStatus.UNPROCESSABLE_ENTITY,
                "EMAIL_RECIPIENT_UNDELIVERABLE",
                "Your registered email is a demo address and cannot receive email. Use an account with a real email address or update your profile.");
    }

    public static EmailDeliveryException unavailable() {
        return new EmailDeliveryException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_DELIVERY_UNAVAILABLE",
                "The email could not be sent. Check your registered email address or try again later.");
    }
}
