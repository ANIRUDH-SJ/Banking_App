package com.netbanking.otp.service;

import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.otp.domain.*;
import com.netbanking.otp.repository.OtpVerificationRepository;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class OtpService {
    private final OtpVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpDeliveryService deliveryService;
    private final AppUserRepository userRepository;
    private final OtpVerificationAttemptService verificationAttemptService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration expiry;
    private final int maxIssuesPerWindow;
    private final Duration issueWindow;

    public OtpService(
            OtpVerificationRepository repository,
            PasswordEncoder passwordEncoder,
            OtpDeliveryService deliveryService,
            AppUserRepository userRepository,
            OtpVerificationAttemptService verificationAttemptService,
            @Value("${app.security.otp-expiry-minutes:5}") long expiryMinutes,
            @Value("${app.security.payment-otp-max-issues:3}") int maxIssuesPerWindow,
            @Value("${app.security.payment-otp-issue-window-minutes:15}") long issueWindowMinutes) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.deliveryService = deliveryService;
        this.userRepository = userRepository;
        this.verificationAttemptService = verificationAttemptService;
        this.expiry = Duration.ofMinutes(expiryMinutes);
        this.maxIssuesPerWindow = maxIssuesPerWindow;
        this.issueWindow = Duration.ofMinutes(issueWindowMinutes);
    }

    public Challenge issue(AppUser user, OtpPurpose purpose) {
        return issue(user, purpose, null);
    }

    public Challenge issue(AppUser user, OtpPurpose purpose, String intentDigest) {
        userRepository
                .findByIdForUpdate(user.getUserId())
                .orElseThrow(
                        () ->
                                new UnauthorizedException(
                                        "User is not eligible for an OTP challenge."));
        Instant now = Instant.now();
        if (intentDigest != null) {
            long recentIssues =
                    repository.countByUserIdAndPurposeAndCreatedAtAfter(
                            user.getUserId(), purpose, now.minus(issueWindow));
            if (recentIssues >= maxIssuesPerWindow) {
                throw new IllegalStateException(
                        "Too many OTP challenges have been requested. Please try again later.");
            }
            repository.expirePendingByUserIdAndPurpose(
                    user.getUserId(), purpose, OtpStatus.PENDING, OtpStatus.EXPIRED);
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String challengeId = UUID.randomUUID().toString();
        repository.saveAndFlush(
                new OtpVerification(
                        user.getUserId(),
                        challengeId,
                        passwordEncoder.encode(code),
                        purpose,
                        now.plus(expiry),
                        intentDigest));
        deliveryService.deliver(user.getUserId(), purpose, code);
        return new Challenge(challengeId);
    }

    public void verifyForUser(Long userId, String challengeId, String code, OtpPurpose purpose) {
        verifyForUser(userId, challengeId, code, purpose, null);
    }

    public void verifyForUser(
            Long userId, String challengeId, String code, OtpPurpose purpose, String intentDigest) {
        OtpVerificationAttemptService.Result result =
                verificationAttemptService.verify(userId, challengeId, code, purpose, intentDigest);
        switch (result) {
            case VERIFIED -> {
                return;
            }
            case EXPIRED -> throw new UnauthorizedException("OTP challenge has expired.");
            case INTENT_MISMATCH ->
                    throw new UnauthorizedException("OTP challenge does not match this request.");
            case INVALID, INVALID_CODE ->
                    throw new UnauthorizedException("OTP challenge or code is invalid.");
        }
    }

    public record Challenge(String challengeId) {}
}
