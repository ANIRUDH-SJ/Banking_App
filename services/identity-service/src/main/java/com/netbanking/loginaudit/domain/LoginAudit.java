package com.netbanking.loginaudit.domain;

import com.netbanking.user.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "login_audit")
public class LoginAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_audit_id")
    private Long loginAuditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "username_attempted", nullable = false, length = 100)
    private String usernameAttempted;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_outcome", nullable = false, length = 20)
    private LoginOutcome loginOutcome;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "client_ip_address", length = 45)
    private String clientIpAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LoginAudit() {}

    private LoginAudit(
            AppUser user,
            String usernameAttempted,
            LoginOutcome loginOutcome,
            String failureReason,
            String clientIpAddress,
            String userAgent,
            Instant occurredAt) {
        this.user = user;
        this.usernameAttempted = limited(usernameAttempted, 100, "unknown");
        this.loginOutcome = loginOutcome;
        this.failureReason = limited(failureReason, 200, null);
        this.clientIpAddress = limited(clientIpAddress, 45, null);
        this.userAgent = limited(userAgent, 500, null);
        this.occurredAt = occurredAt;
    }

    public static LoginAudit success(
            AppUser user, String clientIpAddress, String userAgent, Instant occurredAt) {
        return new LoginAudit(
                user,
                user.getUsername(),
                LoginOutcome.SUCCESS,
                null,
                clientIpAddress,
                userAgent,
                occurredAt);
    }

    public static LoginAudit failure(
            AppUser user,
            String usernameAttempted,
            String failureReason,
            String clientIpAddress,
            String userAgent,
            Instant occurredAt) {
        return new LoginAudit(
                user,
                usernameAttempted,
                LoginOutcome.FAILURE,
                failureReason,
                clientIpAddress,
                userAgent,
                occurredAt);
    }

    public AppUser getUser() {
        return user;
    }

    public String getUsernameAttempted() {
        return usernameAttempted;
    }

    public LoginOutcome getLoginOutcome() {
        return loginOutcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static String limited(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
