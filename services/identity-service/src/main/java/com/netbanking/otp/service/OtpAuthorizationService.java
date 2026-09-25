package com.netbanking.otp.service;

import com.netbanking.discovery.OtpClient.Authorization;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.domain.OtpStatus;
import com.netbanking.otp.repository.OtpVerificationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OtpAuthorizationService {
    private final JdbcTemplate jdbc;
    private final OtpVerificationRepository repository;
    private final PasswordEncoder encoder;
    private final int maxAttempts;

    public OtpAuthorizationService(
            JdbcTemplate jdbc,
            OtpVerificationRepository repository,
            PasswordEncoder encoder,
            @Value("${app.security.otp-max-attempts:5}") int maxAttempts) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.encoder = encoder;
        this.maxAttempts = maxAttempts;
    }

    // The grant and successful OTP consumption commit together. Invalid attempts also commit.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean authorize(String caller, Authorization request) {
        List<String> prior =
                jdbc.query(
                        "SELECT intent_digest FROM otp_authorization WHERE caller = ? AND"
                                + " operation_id = ? AND user_id = ? AND purpose = ?",
                        (rs, n) -> rs.getString(1),
                        caller,
                        request.operationId(),
                        request.userId(),
                        request.purpose());
        if (!prior.isEmpty()) return prior.get(0).equals(request.intentDigest());
        var otp = repository.findByChallengeIdForUpdate(request.challengeId()).orElse(null);
        // Recheck after the OTP row lock: a concurrent request may have committed the same grant.
        prior =
                jdbc.query(
                        "SELECT intent_digest FROM otp_authorization WHERE caller = ? AND"
                                + " operation_id = ? AND user_id = ? AND purpose = ?",
                        (rs, n) -> rs.getString(1),
                        caller,
                        request.operationId(),
                        request.userId(),
                        request.purpose());
        if (!prior.isEmpty()) return prior.get(0).equals(request.intentDigest());
        if (otp == null
                || !otp.getUserId().equals(request.userId())
                || otp.getPurpose() != OtpPurpose.valueOf(request.purpose())
                || otp.getStatus() != OtpStatus.PENDING) return false;
        if (otp.isExpired(Instant.now())) {
            otp.markExpired();
            return false;
        }
        if (!otp.matchesIntent(request.intentDigest())
                || !encoder.matches(request.code(), otp.getOtpHash())) {
            otp.recordFailure(maxAttempts);
            return false;
        }
        otp.markVerified();
        jdbc.update(
                "INSERT INTO otp_authorization (caller, operation_id, user_id, purpose,"
                        + " intent_digest) VALUES (?, ?, ?, ?, ?)",
                caller,
                request.operationId(),
                request.userId(),
                request.purpose(),
                request.intentDigest());
        return true;
    }
}
