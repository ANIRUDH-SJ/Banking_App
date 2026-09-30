package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {
    @Mock private JavaMailSender mailSender;

    @Test
    void sendsPlainTextEmailThroughTheConfiguredMailSender() {
        SmtpEmailService service =
                new SmtpEmailService(mailSender, " no-reply@bank.example ");

        service.send("asha@example.com", "Payment received", "Your payment was received.");

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@bank.example");
        assertThat(message.getValue().getTo()).containsExactly("asha@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Payment received");
        assertThat(message.getValue().getText()).isEqualTo("Your payment was received.");
    }

    @Test
    void rejectsAnEmptySenderAddress() {
        assertThatThrownBy(() -> new SmtpEmailService(mailSender, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An SMTP sender address is required.");
    }
}
