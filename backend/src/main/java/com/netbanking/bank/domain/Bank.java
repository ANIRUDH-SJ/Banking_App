package com.netbanking.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank")
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_id")
    private Long bankId;

    @Column(name = "bank_code", nullable = false, unique = true)
    private String bankCode;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_active", nullable = false, columnDefinition = "CHAR(1)")
    private String isActive;

    protected Bank() {
    }

    public Long getBankId() { return bankId; }
    public String getBankCode() { return bankCode; }
    public String getLegalName() { return legalName; }
    public String getDisplayName() { return displayName; }
    public String getIsActive() { return isActive; }
}
