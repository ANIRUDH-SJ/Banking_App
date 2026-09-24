package com.netbanking.otp.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OtpVerificationTest {
    @Test
    void expiredChallengesAreDetectedAndCannotRemainPending() {
        OtpVerification otp = new OtpVerification(1L, "challenge", "hash", OtpPurpose.LOGIN, Instant.now().minusSeconds(1));
        assertTrue(otp.isExpired(Instant.now()));
        otp.markExpired();
        assertEquals(OtpStatus.EXPIRED, otp.getStatus());
    }

    @Test
    void failedAttemptsEventuallyFailTheChallenge() {
        OtpVerification otp = new OtpVerification(1L, "challenge", "hash", OtpPurpose.LOGIN, Instant.now().plusSeconds(300));
        otp.recordFailure(2);
        assertEquals(OtpStatus.PENDING, otp.getStatus());
        otp.recordFailure(2);
        assertEquals(OtpStatus.FAILED, otp.getStatus());
    }
}
