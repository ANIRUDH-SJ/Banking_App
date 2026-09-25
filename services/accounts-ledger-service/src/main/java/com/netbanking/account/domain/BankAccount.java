package com.netbanking.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_account")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "currency_code", nullable = false, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "account_status", nullable = false)
    private String accountStatus;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected BankAccount() {}

    public Long getAccountId() {
        return accountId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void changeStatus(String accountStatus) {
        this.accountStatus = accountStatus;
        this.closedAt = "CLOSED".equals(accountStatus) ? LocalDateTime.now() : null;
    }

    public void credit(BigDecimal amount) {
        if (!"ACTIVE".equals(accountStatus))
            throw new IllegalStateException("The destination account must be active.");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Credit amount must be positive.");
        currentBalance = currentBalance.add(amount);
        availableBalance = availableBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (!"ACTIVE".equals(accountStatus)) {
            throw new IllegalStateException("The source account must be active.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive.");
        }
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "The source account has insufficient available balance.");
        }
        currentBalance = currentBalance.subtract(amount);
        availableBalance = availableBalance.subtract(amount);
    }
}
