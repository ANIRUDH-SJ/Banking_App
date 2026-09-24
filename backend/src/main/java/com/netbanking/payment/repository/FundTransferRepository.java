package com.netbanking.payment.repository;
import com.netbanking.payment.domain.FundTransfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FundTransferRepository extends JpaRepository<FundTransfer, Long> { Optional<FundTransfer> findByInitiatedByUserIdAndIdempotencyKey(Long userId, String idempotencyKey); }
