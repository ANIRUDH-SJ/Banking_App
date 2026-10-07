package com.netbanking.user.service;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.AccountLockedException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.events.NotificationPublisher;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Transactional
public class UserService {
    private final AppUserRepository userRepository;
    private final IdentityAuditService audit;
    private final NotificationPublisher notifications;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public UserService(
            AppUserRepository userRepository,
            IdentityAuditService audit,
            NotificationPublisher notifications,
            @Value("${app.security.max-failed-login-attempts}") int maxFailedAttempts,
            @Value("${app.security.account-lock-minutes}") long accountLockMinutes) {
        this.userRepository = userRepository;
        this.audit = audit;
        this.notifications = notifications;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(accountLockMinutes);
    }

    @Transactional(readOnly = true)
    public AppUser requireByUsernameOrEmail(String identifier) {
        return userRepository
                .findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password."));
    }

    @Transactional(readOnly = true)
    public AppUser requireById(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
    }

    public void requireEligibleForLogin(AppUser user) {
        Instant now = Instant.now();
        if (user.isLockedAt(now)) {
            long remainingMillis = Duration.between(now, user.getLockedUntil()).toMillis();
            throw new AccountLockedException(Math.max(1, (remainingMillis + 999) / 1000));
        }
        if (user.getAccountStatus() == UserStatus.LOCKED) user.unlock();
        if (user.getAccountStatus() != UserStatus.ACTIVE)
            throw new UnauthorizedException("Account is not active.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(Long userId) {
        AppUser user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
        Instant now = Instant.now();
        if (user.getAccountStatus() == UserStatus.LOCKED && !user.isLockedAt(now)) {
            user.unlock();
        }
        user.recordFailedLogin(maxFailedAttempts, now.plus(lockDuration));
        boolean locked = user.getAccountStatus() == UserStatus.LOCKED;
        audit.denied(
                userId,
                locked ? "ACCOUNT_LOCKED" : "LOGIN_REJECTED",
                "USER",
                String.valueOf(userId),
                "invalidCredentials");
        if (locked) {
            notifications.publish(
                    userId,
                    "SECURITY",
                    "Account temporarily locked",
                    "Your account was temporarily locked after repeated failed sign-in attempts.");
        }
    }

    public void recordSuccessfulLogin(AppUser user) {
        user.recordSuccessfulLogin(Instant.now());
        audit.success(user.getUserId(), "LOGIN_SUCCEEDED", "USER", String.valueOf(user.getUserId()));
        notifications.publish(
                user.getUserId(),
                "SECURITY",
                "New sign-in",
                "A successful sign-in to your internet banking account was recorded.");
    }
}
