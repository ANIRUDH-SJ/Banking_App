package com.netbanking.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentIntentTest {
    @Test
    void transferIntentChangesWhenAnyMaterialPaymentFieldChanges() {
        String original = PaymentIntent.transferDigest(1L, 2L, 3L, new BigDecimal("10.00"));
        assertThat(PaymentIntent.transferDigest(1L, 2L, 3L, new BigDecimal("10.0000"))).isEqualTo(original);
        assertThat(PaymentIntent.transferDigest(1L, 2L, 4L, new BigDecimal("10.00"))).isNotEqualTo(original);
        assertThat(PaymentIntent.transferDigest(1L, 2L, 3L, new BigDecimal("11.00"))).isNotEqualTo(original);
    }

    @Test
    void billPaymentFingerprintBindsTheBillReference() {
        String original = PaymentIntent.billPaymentFingerprint(1L, 2L, 3L, new BigDecimal("10.00"), "ABC-123");
        assertThat(PaymentIntent.billPaymentFingerprint(1L, 2L, 3L, new BigDecimal("10.00"), "ABC-124"))
                .isNotEqualTo(original);
    }
}
