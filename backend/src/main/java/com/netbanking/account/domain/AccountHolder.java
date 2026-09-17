package com.netbanking.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(AccountHolderId.class)
@Table(name = "account_holder")
public class AccountHolder {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "holder_type", nullable = false)
    private String holderType;

    @Column(name = "is_active", nullable = false, columnDefinition = "CHAR(1)")
    private String isActive;

    protected AccountHolder() {
    }

    public Long getAccountId() { return accountId; }
    public Long getCustomerId() { return customerId; }
    public String getHolderType() { return holderType; }
    public String getIsActive() { return isActive; }
}
