package com.netbanking.branch.repository;

import com.netbanking.branch.domain.Branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBankIdAndIsActiveOrderByBranchNameAsc(Long bankId, String isActive);

    Optional<Branch> findByIfscCodeAndIsActive(String ifscCode, String isActive);
}
