package com.netbanking.totp.repository;

import com.netbanking.totp.domain.UserTotp;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserTotpRepository extends JpaRepository<UserTotp, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from UserTotp credential where credential.userId = :userId")
    Optional<UserTotp> findByUserIdForUpdate(@Param("userId") Long userId);
}
