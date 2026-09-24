package com.netbanking.otp.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.otp.domain.*;
import com.netbanking.otp.repository.OtpVerificationRepository;
import com.netbanking.user.domain.AppUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OtpService {
    private final OtpVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpDeliveryService deliveryService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration expiry;
    private final int maxAttempts;
    private final int maxIssuesPerWindow;
    private final Duration issueWindow;
    public OtpService(OtpVerificationRepository repository, PasswordEncoder passwordEncoder, OtpDeliveryService deliveryService,
                      @Value("${app.security.otp-expiry-minutes:5}") long expiryMinutes,
                      @Value("${app.security.otp-max-attempts:5}") int maxAttempts,
                      @Value("${app.security.payment-otp-max-issues:3}") int maxIssuesPerWindow,
                      @Value("${app.security.payment-otp-issue-window-minutes:15}") long issueWindowMinutes) {
        this.repository = repository; this.passwordEncoder = passwordEncoder; this.deliveryService = deliveryService; this.expiry = Duration.ofMinutes(expiryMinutes); this.maxAttempts = maxAttempts; this.maxIssuesPerWindow = maxIssuesPerWindow; this.issueWindow = Duration.ofMinutes(issueWindowMinutes);
    }
    public Challenge issue(AppUser user, OtpPurpose purpose) { return issue(user, purpose, null); }
    public Challenge issue(AppUser user, OtpPurpose purpose, String intentDigest) {
        if (intentDigest != null) {
            if (repository.countByUserIdAndPurposeAndCreatedAtAfter(user.getUserId(), purpose, Instant.now().minus(issueWindow)) >= maxIssuesPerWindow) throw new IllegalStateException("Too many payment OTP challenges have been requested. Please try again later.");
            repository.expirePendingByUserIdAndPurpose(user.getUserId(), purpose, OtpStatus.PENDING, OtpStatus.EXPIRED);
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String challengeId = UUID.randomUUID().toString();
        repository.save(new OtpVerification(user.getUserId(), challengeId, passwordEncoder.encode(code), purpose, Instant.now().plus(expiry), intentDigest));
        deliveryService.deliver(user.getUserId(), purpose, code);
        return new Challenge(challengeId, code);
    }
    public void verifyForUser(Long userId, String challengeId, String code, OtpPurpose purpose) { verifyForUser(userId, challengeId, code, purpose, null); }
    public void verifyForUser(Long userId, String challengeId, String code, OtpPurpose purpose, String intentDigest) {
        OtpVerification otp = repository.findByChallengeIdForUpdate(challengeId).orElseThrow(() -> new ResourceNotFoundException("OTP challenge was not found."));
        if (!otp.getUserId().equals(userId) || otp.getPurpose() != purpose || otp.getStatus() != OtpStatus.PENDING) throw new UnauthorizedException("OTP challenge is not valid.");
        if (intentDigest != null && !otp.matchesIntent(intentDigest)) throw new UnauthorizedException("OTP challenge does not match this payment request.");
        if (otp.isExpired(Instant.now())) { otp.markExpired(); throw new UnauthorizedException("OTP challenge has expired."); }
        if (!passwordEncoder.matches(code, otp.getOtpHash())) { otp.recordFailure(maxAttempts); throw new UnauthorizedException("OTP code is invalid."); }
        otp.markVerified();
    }
    public record Challenge(String challengeId, String code) { }
}
