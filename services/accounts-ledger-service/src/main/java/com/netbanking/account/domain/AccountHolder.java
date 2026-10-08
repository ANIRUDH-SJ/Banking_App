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

    @Column(name = "nickname", length = 40)
    private String nickname;

    protected AccountHolder() {}

    public AccountHolder(Long accountId, Long customerId) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.holderType = "PRIMARY";
        this.isActive = "Y";
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getHolderType() {
        return holderType;
    }

    public String getIsActive() {
        return isActive;
    }

    public String getNickname() {
        return nickname;
    }

    /** Sets or clears this holder's label; the account number and identity are unaffected. */
    public void rename(String nickname) {
        String value = nickname == null ? "" : nickname.strip().replaceAll("\\s+", " ");
        if (value.length() > 40) {
            throw new IllegalArgumentException("Keep the nickname to 40 characters or fewer.");
        }
        if (!value.isEmpty() && !value.matches("[\\p{L}\\p{N} '&().,/-]+")) {
            throw new IllegalArgumentException(
                    "Use letters, numbers, spaces and simple punctuation in the nickname.");
        }
        this.nickname = value.isEmpty() ? null : value;
    }
}
