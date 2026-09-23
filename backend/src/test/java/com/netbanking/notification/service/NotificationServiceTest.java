package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private AuditLogService auditLogService;

    @Test
    void recordsAnAuditEventWhenCreatingANotification() {
        Notification notification = new Notification(7L, "ACCOUNT", "Deposit received", "A deposit was received.");
        when(repository.save(any(Notification.class))).thenReturn(notification);

        NotificationService service = new NotificationService(repository, auditLogService);
        service.createInApp(7L, "ACCOUNT", "Deposit received", "A deposit was received.");

        verify(auditLogService).record(eq(7L), eq("NOTIFICATION_CREATED"), eq("NOTIFICATION"), any(), eq("SUCCESS"));
    }

    @Test
    void preventsOneUserFromMarkingAnotherUsersNotificationAsRead() {
        Notification notification = new Notification(7L, "ACCOUNT", "Deposit received", "A deposit was received.");
        when(repository.findById(15L)).thenReturn(Optional.of(notification));

        NotificationService service = new NotificationService(repository, auditLogService);

        assertThatThrownBy(() -> service.markRead(8L, 15L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void letsTheOwnerMarkTheirNotificationAsRead() {
        Notification notification = new Notification(7L, "ACCOUNT", "Deposit received", "A deposit was received.");
        when(repository.findById(15L)).thenReturn(Optional.of(notification));

        NotificationService service = new NotificationService(repository, auditLogService);

        assertThatCode(() -> service.markRead(7L, 15L)).doesNotThrowAnyException();
    }
}
