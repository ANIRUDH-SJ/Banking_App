package com.netbanking.otp.repository;
import com.netbanking.otp.domain.OtpVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> { Optional<OtpVerification> findByChallengeId(String challengeId); }
