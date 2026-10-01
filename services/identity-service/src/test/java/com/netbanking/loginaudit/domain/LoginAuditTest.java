package com.netbanking.loginaudit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.user.domain.AppUser;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class LoginAuditTest {

    @Test
    void successCapturesTheKnownUserWithoutAFailureReason() {
        AppUser user = new AppUser("asha", "asha@example.com", "hash");
        Instant occurredAt = Instant.parse("2026-09-29T12:00:00Z");

        LoginAudit audit = LoginAudit.success(user, "203.0.113.8", "test-agent", occurredAt);

        assertThat(audit.getUser()).isSameAs(user);
        assertThat(audit.getUsernameAttempted()).isEqualTo("asha");
        assertThat(audit.getLoginOutcome()).isEqualTo(LoginOutcome.SUCCESS);
        assertThat(audit.getFailureReason()).isNull();
        assertThat(audit.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void failureBoundsUntrustedMetadataToDatabaseColumnLengths() {
        LoginAudit audit =
                LoginAudit.failure(
                        null,
                        "u".repeat(120),
                        "r".repeat(220),
                        "i".repeat(60),
                        "a".repeat(520),
                        Instant.EPOCH);

        assertThat(audit.getUsernameAttempted()).hasSize(100);
        assertThat(audit.getFailureReason()).hasSize(200);
        assertThat(audit.getClientIpAddress()).hasSize(45);
        assertThat(audit.getUserAgent()).hasSize(500);
        assertThat(audit.getLoginOutcome()).isEqualTo(LoginOutcome.FAILURE);
    }
}
