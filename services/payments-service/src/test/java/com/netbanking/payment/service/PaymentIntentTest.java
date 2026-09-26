package com.netbanking.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class PaymentIntentTest {
    @Test
    void normalizesEquivalentAmountsAndWhitespace() {
        String first =
                PaymentIntent.billPaymentFingerprint(
                        7L, 10L, 20L, new BigDecimal("100.00"), "  REF123  ");
        String second =
                PaymentIntent.billPaymentFingerprint(7L, 10L, 20L, new BigDecimal("100"), "REF123");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void changesWhenAnyMaterialPaymentFieldChanges() {
        String original = PaymentIntent.transferDigest(7L, 10L, 20L, new BigDecimal("100"));

        assertThat(PaymentIntent.transferDigest(7L, 10L, 21L, new BigDecimal("100")))
                .isNotEqualTo(original);
        assertThat(PaymentIntent.transferDigest(7L, 10L, 20L, new BigDecimal("101")))
                .isNotEqualTo(original);
    }
}
