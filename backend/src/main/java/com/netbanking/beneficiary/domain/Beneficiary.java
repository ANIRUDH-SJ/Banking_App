package com.netbanking.beneficiary.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "beneficiary")
public class Beneficiary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "beneficiary_id") private Long beneficiaryId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(nullable = false) private String nickname;
    @Column(name = "beneficiary_name", nullable = false) private String beneficiaryName;
    @Column(name = "account_number", nullable = false) private String accountNumber;
    @Column(name = "ifsc_code", nullable = false) private String ifscCode;
    @Column(name = "bank_name", nullable = false) private String bankName;
    @Column(name = "beneficiary_status", nullable = false) private String beneficiaryStatus;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Beneficiary() { }
    public Beneficiary(Long customerId, String nickname, String beneficiaryName, String accountNumber, String ifscCode, String bankName) {
        this.customerId = customerId; this.nickname = nickname; this.beneficiaryName = beneficiaryName;
        this.accountNumber = accountNumber; this.ifscCode = ifscCode; this.bankName = bankName; this.beneficiaryStatus = "PENDING";
    }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public Long getCustomerId() { return customerId; }
    public String getNickname() { return nickname; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getAccountNumber() { return accountNumber; }
    public String getIfscCode() { return ifscCode; }
    public String getBankName() { return bankName; }
    public String getBeneficiaryStatus() { return beneficiaryStatus; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return "ACTIVE".equals(beneficiaryStatus); }
    public void activate() {
        if (!"PENDING".equals(beneficiaryStatus)) throw new IllegalStateException("Only a pending beneficiary can be activated.");
        beneficiaryStatus = "ACTIVE"; activatedAt = Instant.now();
    }
    public void disable() { beneficiaryStatus = "DISABLED"; }
    @PrePersist void initializeCreatedAt() { if (createdAt == null) createdAt = Instant.now(); }
}
