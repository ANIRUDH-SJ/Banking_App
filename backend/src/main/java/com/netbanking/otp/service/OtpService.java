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
    public OtpService(OtpVerificationRepository repository, PasswordEncoder passwordEncoder, OtpDeliveryService deliveryService,
                      @Value("${app.security.otp-expiry-minutes:5}") long expiryMinutes,
                      @Value("${app.security.otp-max-attempts:5}") int maxAttempts) {
        this.repository = repository; this.passwordEncoder = passwordEncoder; this.deliveryService = deliveryService; this.expiry = Duration.ofMinutes(expiryMinutes); this.maxAttempts = maxAttempts;
    }
    public Challenge issue(AppUser user, OtpPurpose purpose) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String challengeId = UUID.randomUUID().toString();
        repository.save(new OtpVerification(user.getUserId(), challengeId, passwordEncoder.encode(code), purpose, Instant.now().plus(expiry)));
        deliveryService.deliver(user.getUserId(), purpose, code);
        return new Challenge(challengeId, code);
    }
    public void verifyForUser(Long userId, String challengeId, String code, OtpPurpose purpose) {
        OtpVerification otp = repository.findByChallengeId(challengeId).orElseThrow(() -> new ResourceNotFoundException("OTP challenge was not found."));
        if (!otp.getUserId().equals(userId) || otp.getPurpose() != purpose || otp.getStatus() != OtpStatus.PENDING) throw new UnauthorizedException("OTP challenge is not valid.");
        if (otp.isExpired(Instant.now())) { otp.markExpired(); throw new UnauthorizedException("OTP challenge has expired."); }
        if (!passwordEncoder.matches(code, otp.getOtpHash())) { otp.recordFailure(maxAttempts); throw new UnauthorizedException("OTP code is invalid."); }
        otp.markVerified();
    }
    public record Challenge(String challengeId, String code) { }
}
