package com.netbanking.loginaudit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.netbanking.loginaudit.domain.LoginAudit;
import com.netbanking.loginaudit.domain.LoginOutcome;
import com.netbanking.loginaudit.repository.LoginAuditRepository;
import com.netbanking.user.domain.AppUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@ExtendWith(MockitoExtension.class)
class LoginAuditServiceTest {
    @Mock private LoginAuditRepository repository;

    @Test
    void recordsFailureWithAStableTimestampAndClientContext() {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        LoginAuditService service =
                new LoginAuditService(repository, Clock.fixed(now, ZoneOffset.UTC));
        LoginAttemptContext context = new LoginAttemptContext("203.0.113.8", "test-agent");

        service.failure(null, "missing", "UNKNOWN_PRINCIPAL", context);

        ArgumentCaptor<LoginAudit> audit = ArgumentCaptor.forClass(LoginAudit.class);
        verify(repository).save(audit.capture());
        assertThat(audit.getValue().getLoginOutcome()).isEqualTo(LoginOutcome.FAILURE);
        assertThat(audit.getValue().getUsernameAttempted()).isEqualTo("missing");
        assertThat(audit.getValue().getFailureReason()).isEqualTo("UNKNOWN_PRINCIPAL");
        assertThat(audit.getValue().getClientIpAddress()).isEqualTo("203.0.113.8");
        assertThat(audit.getValue().getUserAgent()).isEqualTo("test-agent");
        assertThat(audit.getValue().getOccurredAt()).isEqualTo(now);
    }

    @Test
    void recordsSuccessAgainstTheAuthenticatedUser() {
        LoginAuditService service =
                new LoginAuditService(repository, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        AppUser user = new AppUser("asha", "asha@example.com", "hash");

        service.success(user, new LoginAttemptContext("203.0.113.8", null));

        ArgumentCaptor<LoginAudit> audit = ArgumentCaptor.forClass(LoginAudit.class);
        verify(repository).save(audit.capture());
        assertThat(audit.getValue().getUser()).isSameAs(user);
        assertThat(audit.getValue().getLoginOutcome()).isEqualTo(LoginOutcome.SUCCESS);
    }
}
