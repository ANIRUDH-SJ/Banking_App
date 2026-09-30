package com.netbanking.ledger.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netbanking.common.exception.ConflictException;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

class MockExternalTransferAdapterTest {
    private static ExternalTransferCommand command(String accountNumber) {
        return new ExternalTransferCommand(
                "operation-key",
                accountNumber,
                "WXYZ0001234",
                new BigDecimal("100"),
                "INR",
                "Rent");
    }

    @Test
    void retriesTransientFailureAndReturnsAnIdempotentReceipt() {
        var adapter = new MockExternalTransferAdapter(1);

        assertThatThrownBy(() -> adapter.transfer(command("9999999999")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        failure ->
                                assertThat(((ResponseStatusException) failure).getStatusCode().value())
                                        .isEqualTo(503));

        var accepted = adapter.transfer(command("9999999999"));
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(adapter.transfer(command("9999999999"))).isEqualTo(accepted);
    }

    @Test
    void rejectsReuseOfAnOperationKeyForDifferentInstructions() {
        var adapter = new MockExternalTransferAdapter(0);
        adapter.transfer(command("9999999999"));

        assertThatThrownBy(() -> adapter.transfer(command("8888888888")))
                .isInstanceOf(ConflictException.class);
    }
}
