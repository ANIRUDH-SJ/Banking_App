package com.netbanking.bank.repository;

import com.netbanking.bank.domain.Bank;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findByIsActiveOrderByDisplayNameAsc(String isActive);
}
