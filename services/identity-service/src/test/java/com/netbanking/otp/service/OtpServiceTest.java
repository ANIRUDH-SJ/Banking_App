package com.netbanking.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.domain.OtpStatus;
import com.netbanking.otp.domain.OtpVerification;
import com.netbanking.otp.repository.OtpVerificationRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {
    @Mock private OtpVerificationRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpDeliveryService deliveryService;
    @Mock private AppUserRepository userRepository;
    @Mock private OtpVerificationAttemptService verificationAttemptService;

    @Test
    void serializesIssuanceAndInvalidatesAnOlderPendingChallenge() {
        AppUser user = user();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(repository.countByUserIdAndPurposeAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var challenge = service().issue(user, OtpPurpose.FUND_TRANSFER, "intent");

        assertThat(challenge.challengeId()).isNotBlank();
        verify(repository)
                .expirePendingByUserIdAndPurpose(
                        7L, OtpPurpose.FUND_TRANSFER, OtpStatus.PENDING, OtpStatus.EXPIRED);
        ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIntentDigest()).isEqualTo("intent");
        verify(deliveryService)
                .deliver(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(OtpPurpose.FUND_TRANSFER),
                        any());
    }

    @Test
    void rejectsIssuanceAfterTheConfiguredWindowLimit() {
        AppUser user = user();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(repository.countByUserIdAndPurposeAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(3L);

        assertThatThrownBy(() -> service().issue(user, OtpPurpose.BILL_PAYMENT, "intent"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many");
        verify(deliveryService, never()).deliver(any(), any(), any());
    }

    @Test
    void verifiesAPasswordResetAgainstItsUserBoundIntent() {
        var challenge =
                new OtpVerification(
                        7L,
                        "reset-challenge",
                        "hash",
                        OtpPurpose.PASSWORD_RESET,
                        java.time.Instant.now().plusSeconds(300),
                        com.netbanking.contracts.RequestFingerprint.of("PASSWORD_RESET", 7L));
        when(repository.findByChallengeId("reset-challenge"))
                .thenReturn(Optional.of(challenge));
        when(verificationAttemptService.verify(
                        7L,
                        "reset-challenge",
                        "123456",
                        OtpPurpose.PASSWORD_RESET,
                        com.netbanking.contracts.RequestFingerprint.of("PASSWORD_RESET", 7L)))
                .thenReturn(OtpVerificationAttemptService.Result.VERIFIED);

        assertThat(service().verifyPasswordReset("reset-challenge", "123456")).isEqualTo(7L);
    }

    @Test
    void rejectsAChallengeIssuedForAnotherPurpose() {
        var challenge =
                new OtpVerification(
                        7L,
                        "transfer-challenge",
                        "hash",
                        OtpPurpose.FUND_TRANSFER,
                        java.time.Instant.now().plusSeconds(300));
        when(repository.findByChallengeId("transfer-challenge"))
                .thenReturn(Optional.of(challenge));

        assertThatThrownBy(
                        () -> service().verifyPasswordReset("transfer-challenge", "123456"))
                .isInstanceOf(com.netbanking.common.exception.UnauthorizedException.class);
        verify(verificationAttemptService, never())
                .verify(any(), any(), any(), any(), any());
    }

    private OtpService service() {
        return new OtpService(
                repository,
                passwordEncoder,
                deliveryService,
                userRepository,
                verificationAttemptService,
                5,
                3,
                15);
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
