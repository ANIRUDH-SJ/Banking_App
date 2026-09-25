package com.netbanking.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

class BankAccountDebitTest {

    private BankAccount account;

    @BeforeEach
    void setUp() {
        account = new BankAccount();
        ReflectionTestUtils.setField(account, "accountStatus", "ACTIVE");
        ReflectionTestUtils.setField(account, "currentBalance", new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(account, "availableBalance", new BigDecimal("900.00"));
    }

    @Test
    void debitsCurrentAndAvailableBalances() {
        account.debit(new BigDecimal("200.00"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("800.00");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void rejectsAnAmountAboveTheAvailableBalance() {
        assertThatThrownBy(() -> account.debit(new BigDecimal("950.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void rejectsDebitsFromAnInactiveAccount() {
        ReflectionTestUtils.setField(account, "accountStatus", "FROZEN");

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
    }
}
