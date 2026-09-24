package com.netbanking.otp.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "otp_verification")
public class OtpVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "otp_id") private Long otpId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "challenge_id", nullable = false, unique = true) private String challengeId;
    @Column(name = "otp_hash", nullable = false) private String otpHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OtpPurpose purpose;
    @Enumerated(EnumType.STRING) @Column(name = "otp_status", nullable = false) private OtpStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    protected OtpVerification() { }
    public OtpVerification(Long userId, String challengeId, String otpHash, OtpPurpose purpose, Instant expiresAt) {
        this.userId = userId; this.challengeId = challengeId; this.otpHash = otpHash; this.purpose = purpose; this.expiresAt = expiresAt; this.status = OtpStatus.PENDING;
    }
    public Long getUserId() { return userId; }
    public String getChallengeId() { return challengeId; }
    public String getOtpHash() { return otpHash; }
    public OtpPurpose getPurpose() { return purpose; }
    public OtpStatus getStatus() { return status; }
    public boolean isExpired(Instant now) { return expiresAt.isBefore(now) || expiresAt.equals(now); }
    public void markExpired() { status = OtpStatus.EXPIRED; }
    public void markVerified() { status = OtpStatus.VERIFIED; verifiedAt = Instant.now(); }
    public void recordFailure(int maxAttempts) { failedAttempts++; if (failedAttempts >= maxAttempts) status = OtpStatus.FAILED; }
}
