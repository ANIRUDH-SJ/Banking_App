package com.netbanking.bank.repository;

import com.netbanking.bank.domain.Bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findByIsActiveOrderByDisplayNameAsc(String isActive);
}
