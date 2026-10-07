package com.netbanking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.AccountLockedException;
import com.netbanking.events.NotificationPublisher;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private IdentityAuditService audit;
    @Mock private NotificationPublisher notifications;

    @Test
    void persistsFailedLoginStateInItsOwnTransaction() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        UserService service = new UserService(userRepository, audit, notifications, 1, 15);

        service.recordFailedLogin(7L);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLockedAt(Instant.now())).isTrue();
        verify(userRepository).findByIdForUpdate(7L);
        verify(audit).denied(7L, "ACCOUNT_LOCKED", "USER", "7", "invalidCredentials");
        verify(notifications)
                .publish(
                        7L,
                        "SECURITY",
                        "Account temporarily locked",
                        "Your account was temporarily locked after repeated failed sign-in attempts.");
    }

    @Test
    void temporaryLockGivesWaitTimeAndExpires() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        user.recordFailedLogin(1, Instant.now().plusSeconds(90));
        UserService service = new UserService(userRepository, audit, notifications, 1, 15);

        assertThatThrownBy(() -> service.requireEligibleForLogin(user))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(exception ->
                        assertThat(((AccountLockedException) exception).retryAfterSeconds())
                                .isBetween(1L, 90L));

        ReflectionTestUtils.setField(user, "lockedUntil", Instant.now().minusSeconds(1));
        service.requireEligibleForLogin(user);
        assertThat(user.getAccountStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void firstFailedAttemptAfterExpiredLockStartsAtOne() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        user.recordFailedLogin(1, Instant.now().minusSeconds(1));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        UserService service = new UserService(userRepository, audit, notifications, 5, 15);

        service.recordFailedLogin(7L);

        assertThat(user.getAccountStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }
}
