package com.netbanking.biller.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BillerReferenceValidationTest {
    @Test
    void acceptsFiveDigitDemoElectricityReferences() {
        Biller biller = new Biller();
        ReflectionTestUtils.setField(biller, "referencePattern", "^[0-9]{5,20}$");

        assertThatCode(() -> biller.validateReference("56789")).doesNotThrowAnyException();
        assertThatThrownBy(() -> biller.validateReference("5678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesTheConfiguredBillerPattern() {
        Biller biller = new Biller();
        ReflectionTestUtils.setField(biller, "referencePattern", "^[0-9]{10}$");

        assertThatCode(() -> biller.validateReference("9876543210")).doesNotThrowAnyException();
        assertThatThrownBy(() -> biller.validateReference("not-a-number"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
