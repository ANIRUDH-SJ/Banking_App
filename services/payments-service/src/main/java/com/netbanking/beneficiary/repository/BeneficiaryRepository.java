package com.netbanking.beneficiary.repository;

import com.netbanking.beneficiary.domain.Beneficiary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findAllByCustomerIdOrderByNicknameAsc(Long customerId);

    Optional<Beneficiary> findByBeneficiaryIdAndCustomerId(Long beneficiaryId, Long customerId);

    boolean existsByCustomerIdAndNicknameIgnoreCase(Long customerId, String nickname);

    boolean existsByCustomerIdAndAccountNumberAndIfscCode(
            Long customerId, String accountNumber, String ifscCode);
}
