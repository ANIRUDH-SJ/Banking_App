package com.netbanking.account.repository;

import com.netbanking.account.domain.BankAccount;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    @Query(
            "select account.accountId from BankAccount account where account.accountNumber ="
                    + " :accountNumber")
    Optional<Long> findAccountIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BankAccount account where account.accountId = :accountId")
    Optional<BankAccount> findByIdForUpdate(@Param("accountId") Long accountId);
}
