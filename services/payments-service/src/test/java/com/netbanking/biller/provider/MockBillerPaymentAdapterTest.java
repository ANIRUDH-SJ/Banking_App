package com.netbanking.biller.provider;

import static org.assertj.core.api.Assertions.*;

import com.netbanking.common.exception.ConflictException;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

class MockBillerPaymentAdapterTest {
    BillerPaymentCommand command(String reference) {
        return new BillerPaymentCommand(
                "operation-1", "WATER", reference, new BigDecimal("100"), "INR");
    }

    @Test
    void retriesTransientFailureAndReturnsTheSameAcceptedReceipt() {
        var adapter = new MockBillerPaymentAdapter(1, "");

        assertThatThrownBy(() -> adapter.collect(command("ABC-123")))
                .isInstanceOf(ResponseStatusException.class);
        var first = adapter.collect(command("ABC-123"));

        assertThat(first.status()).isEqualTo("ACCEPTED");
        assertThat(adapter.collect(command("ABC-123"))).isEqualTo(first);
    }

    @Test
    void rejectsConfiguredReferencesAndProtectsTheIdempotencyKey() {
        var adapter = new MockBillerPaymentAdapter(0, "REJECT-");

        assertThat(adapter.collect(command("REJECT-123")).status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> adapter.collect(command("ABC-123")))
                .isInstanceOf(ConflictException.class);
    }
}
