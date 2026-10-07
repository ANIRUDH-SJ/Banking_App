package com.netbanking.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A credit-card purchase repaid in equal monthly instalments. */
@Entity
@Table(name = "card_emi_plan")
public class CardEmiPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emi_plan_id")
    private Long emiPlanId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "card_transaction_id", nullable = false, unique = true)
    private Long cardTransactionId;

    @Column(name = "request_key", nullable = false, length = 64)
    private String requestKey;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "annual_interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "monthly_instalment", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyInstalment;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInterest;

    @Column(name = "total_payable", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPayable;

    @Column(name = "instalments_paid", nullable = false)
    private Integer instalmentsPaid = 0;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Column(name = "plan_status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CardEmiPlan() {}

    public CardEmiPlan(
            Long cardId,
            Long cardTransactionId,
            String requestKey,
            BigDecimal principal,
            int tenureMonths,
            BigDecimal annualInterestRate,
            BigDecimal monthlyInstalment,
            BigDecimal totalInterest,
            BigDecimal totalPayable,
            LocalDate firstDueDate,
            LocalDateTime createdAt) {
        this.cardId = cardId;
        this.cardTransactionId = cardTransactionId;
        this.requestKey = requestKey;
        this.principal = principal;
        this.tenureMonths = tenureMonths;
        this.annualInterestRate = annualInterestRate;
        this.monthlyInstalment = monthlyInstalment;
        this.totalInterest = totalInterest;
        this.totalPayable = totalPayable;
        this.firstDueDate = firstDueDate;
        this.createdAt = createdAt;
    }

    public Long getEmiPlanId() {
        return emiPlanId;
    }

    public Long getCardId() {
        return cardId;
    }

    public Long getCardTransactionId() {
        return cardTransactionId;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public BigDecimal getMonthlyInstalment() {
        return monthlyInstalment;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public int getInstalmentsPaid() {
        return instalmentsPaid;
    }

    public LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
