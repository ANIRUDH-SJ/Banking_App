package com.netbanking.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.domain.OtpStatus;
import com.netbanking.otp.domain.OtpVerification;
import com.netbanking.otp.repository.OtpVerificationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OtpVerificationAttemptServiceTest {
    @Mock private OtpVerificationRepository repository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void locksAndConsumesAChallengeExactlyOnce() {
        OtpVerification otp = otp(Instant.now().plusSeconds(60), "intent");
        when(repository.findByChallengeIdForUpdate("challenge")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        var result = service().verify(7L, "challenge", "123456",
                OtpPurpose.FUND_TRANSFER, "intent");

        assertThat(result).isEqualTo(OtpVerificationAttemptService.Result.VERIFIED);
        assertThat(otp.getStatus()).isEqualTo(OtpStatus.VERIFIED);
        verify(repository).findByChallengeIdForUpdate("challenge");
    }

    @Test
    void persistsFailedAttemptsBeforeReportingAnInvalidCode() {
        OtpVerification otp = otp(Instant.now().plusSeconds(60), "intent");
        when(repository.findByChallengeIdForUpdate("challenge")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        var result = service().verify(7L, "challenge", "000000",
                OtpPurpose.FUND_TRANSFER, "intent");

        assertThat(result).isEqualTo(OtpVerificationAttemptService.Result.INVALID_CODE);
        assertThat(otp.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void expiresAStaleChallengeInsideTheLockedTransaction() {
        OtpVerification otp = otp(Instant.now().minusSeconds(1), "intent");
        when(repository.findByChallengeIdForUpdate("challenge")).thenReturn(Optional.of(otp));

        var result = service().verify(7L, "challenge", "123456",
                OtpPurpose.FUND_TRANSFER, "intent");

        assertThat(result).isEqualTo(OtpVerificationAttemptService.Result.EXPIRED);
        assertThat(otp.getStatus()).isEqualTo(OtpStatus.EXPIRED);
    }

    private OtpVerificationAttemptService service() {
        return new OtpVerificationAttemptService(repository, passwordEncoder, 3);
    }

    private static OtpVerification otp(Instant expiresAt, String intent) {
        return new OtpVerification(7L, "challenge", "hash",
                OtpPurpose.FUND_TRANSFER, expiresAt, intent);
    }
}
