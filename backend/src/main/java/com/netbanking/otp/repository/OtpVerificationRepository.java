package com.netbanking.otp.repository;
import com.netbanking.otp.domain.OtpVerification;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.domain.OtpStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findByChallengeId(String challengeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select otp from OtpVerification otp where otp.challengeId = :challengeId")
    Optional<OtpVerification> findByChallengeIdForUpdate(@Param("challengeId") String challengeId);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, OtpPurpose purpose, Instant createdAfter);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OtpVerification otp set otp.status = :expired where otp.userId = :userId "
            + "and otp.purpose = :purpose and otp.status = :pending")
    int expirePendingByUserIdAndPurpose(@Param("userId") Long userId,
                                        @Param("purpose") OtpPurpose purpose,
                                        @Param("pending") OtpStatus pending,
                                        @Param("expired") OtpStatus expired);
}
