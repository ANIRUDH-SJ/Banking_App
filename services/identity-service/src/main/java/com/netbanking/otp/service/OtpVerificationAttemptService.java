package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.domain.OtpStatus;
import com.netbanking.otp.domain.OtpVerification;
import com.netbanking.otp.repository.OtpVerificationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OtpVerificationAttemptService {
    private final OtpVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final int maxAttempts;

    public OtpVerificationAttemptService(
            OtpVerificationRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.otp-max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result verify(
            Long userId, String challengeId, String code, OtpPurpose purpose, String intentDigest) {
        OtpVerification otp = repository.findByChallengeIdForUpdate(challengeId).orElse(null);
        if (otp == null
                || !otp.getUserId().equals(userId)
                || otp.getPurpose() != purpose
                || otp.getStatus() != OtpStatus.PENDING) {
            return Result.INVALID;
        }
        if (otp.isExpired(Instant.now())) {
            otp.markExpired();
            return Result.EXPIRED;
        }
        if (intentDigest != null && !otp.matchesIntent(intentDigest)) {
            otp.recordFailure(maxAttempts);
            return Result.INTENT_MISMATCH;
        }
        if (!passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.recordFailure(maxAttempts);
            return Result.INVALID_CODE;
        }
        otp.markVerified();
        return Result.VERIFIED;
    }

    public enum Result {
        VERIFIED,
        INVALID,
        EXPIRED,
        INTENT_MISMATCH,
        INVALID_CODE
    }
}
