package com.netbanking.account.domain;

import java.io.Serializable;
import java.util.Objects;

public class AccountHolderId implements Serializable {
    private Long accountId;
    private Long customerId;

    public AccountHolderId() {}

    public AccountHolderId(Long accountId, Long customerId) {
        this.accountId = accountId;
        this.customerId = customerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountHolderId that)) return false;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, customerId);
    }
}
