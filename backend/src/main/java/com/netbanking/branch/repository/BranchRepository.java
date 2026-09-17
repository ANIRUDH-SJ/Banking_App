package com.netbanking.branch.repository;

import com.netbanking.branch.domain.Branch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBankIdAndIsActiveOrderByBranchNameAsc(Long bankId, String isActive);
}
