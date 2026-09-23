package com.netbanking.loan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "loan_account")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "loan_account_number", nullable = false, unique = true, length = 24)
    private String loanAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 20)
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingPrincipal;

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "emi_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal emiAmount;

    @Column(name = "currency_code", nullable = false, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "disbursed_on", nullable = false)
    private LocalDate disbursedOn;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_status", nullable = false, length = 20)
    private LoanStatus loanStatus;

    protected Loan() {
    }

    public Loan(Long customerId, String loanAccountNumber, LoanType loanType,
                BigDecimal principalAmount, BigDecimal outstandingPrincipal,
                BigDecimal interestRate, Integer termMonths, BigDecimal emiAmount,
                String currencyCode, LocalDate disbursedOn, LocalDate nextDueDate,
                LocalDate maturityDate, LoanStatus loanStatus) {
        this.customerId = customerId;
        this.loanAccountNumber = loanAccountNumber;
        this.loanType = loanType;
        this.principalAmount = principalAmount;
        this.outstandingPrincipal = outstandingPrincipal;
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.emiAmount = emiAmount;
        this.currencyCode = currencyCode;
        this.disbursedOn = disbursedOn;
        this.nextDueDate = nextDueDate;
        this.maturityDate = maturityDate;
        this.loanStatus = loanStatus;
    }

    public Long getLoanId() { return loanId; }
    public Long getCustomerId() { return customerId; }
    public String getLoanAccountNumber() { return loanAccountNumber; }
    public LoanType getLoanType() { return loanType; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public BigDecimal getOutstandingPrincipal() { return outstandingPrincipal; }
    public BigDecimal getInterestRate() { return interestRate; }
    public Integer getTermMonths() { return termMonths; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getDisbursedOn() { return disbursedOn; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public LoanStatus getLoanStatus() { return loanStatus; }
}
