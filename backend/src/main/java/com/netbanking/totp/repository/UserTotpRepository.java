package com.netbanking.totp.repository;
import com.netbanking.totp.domain.UserTotp;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserTotpRepository extends JpaRepository<UserTotp, Long> { }
