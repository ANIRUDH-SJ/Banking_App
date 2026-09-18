package com.netbanking.branch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "bank_id", nullable = false)
    private Long bankId;

    @Column(name = "branch_code", nullable = false)
    private String branchCode;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(name = "ifsc_code", nullable = false, unique = true)
    private String ifscCode;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "is_active", nullable = false, columnDefinition = "CHAR(1)")
    private String isActive;

    protected Branch() {
    }

    public Long getBranchId() { return branchId; }
    public Long getBankId() { return bankId; }
    public String getBranchCode() { return branchCode; }
    public String getBranchName() { return branchName; }
    public String getIfscCode() { return ifscCode; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getIsActive() { return isActive; }
}
