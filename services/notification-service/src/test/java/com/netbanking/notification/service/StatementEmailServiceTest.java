package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.discovery.NotificationRecipientDirectory;
import com.netbanking.discovery.NotificationRecipientDirectory.NotificationRecipient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

class StatementEmailServiceTest {
    private final NotificationRecipientDirectory recipients = mock(NotificationRecipientDirectory.class);
    private final EmailService email = mock(EmailService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StatementEmailService service = new StatementEmailService(recipients, email, notifications);
    private final String pdf = Base64.getEncoder().encodeToString("%PDF-1.7 test".getBytes());

    @Test
    void sendsThePdfToTheRegisteredAddressAndReportsItMasked() {
        when(recipients.requireForUser(7L)).thenReturn(new NotificationRecipient("asha@example.in", null));

        var receipt = service.send(command("statement-7731-2026-10-07.pdf"));

        var attachment = ArgumentCaptor.forClass(EmailService.Attachment.class);
        verify(email).send(eq("asha@example.in"), eq("Statement"), eq("Attached."), attachment.capture());
        assertThat(attachment.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(new String(attachment.getValue().content())).startsWith("%PDF");
        assertThat(receipt.status()).isEqualTo("SENT");
        assertThat(receipt.sentTo()).isEqualTo("a***@example.in");
        verify(notifications).createInApp(eq(7L), eq("STATEMENT_EMAILED"), anyString(), anyString());
    }

    @Test
    void aDeliveryFailureIsReportedAndNotRecordedAsSent() {
        when(recipients.requireForUser(7L)).thenReturn(new NotificationRecipient("asha@example.in", null));
        doThrow(new IllegalStateException("smtp down")).when(email).send(anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> service.send(command("statement.pdf")))
                .isInstanceOf(ResponseStatusException.class);
        verify(notifications, never()).createInApp(any(), any(), any(), any());
    }

    @Test
    void rejectsUnsafeFileNames() {
        assertThatThrownBy(() -> service.send(command("../../etc/passwd.pdf")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StatementEmailService.Command command(String filename) {
        return new StatementEmailService.Command(7L, "Statement", "Attached.", filename, pdf);
    }
}
