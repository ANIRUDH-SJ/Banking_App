package com.netbanking.payment.repository;
import com.netbanking.payment.domain.BillPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> { Optional<BillPayment> findByInitiatedByUserIdAndIdempotencyKey(Long userId, String idempotencyKey); }
