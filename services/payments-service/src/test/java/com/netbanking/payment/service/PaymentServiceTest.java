package com.netbanking.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.biller.service.BillerService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.contracts.*;
import com.netbanking.discovery.*;
import com.netbanking.payment.api.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

class PaymentServiceTest {
    final LedgerClient ledger = mock(LedgerClient.class);
    final OtpClient otp = mock(OtpClient.class);
    final PaymentWorkflowStore store = mock(PaymentWorkflowStore.class);
    final PaymentAuditService audit = mock(PaymentAuditService.class);
    final PaymentService service =
            new PaymentService(
                    mock(BeneficiaryService.class),
                    mock(BillerService.class),
                    ledger,
                    otp,
                    store,
                    audit,
                    new BigDecimal("100000"));
    final FundTransferRequest request =
            new FundTransferRequest(
                    10L, 20L, new BigDecimal("100"), "Rent", "request-key", "challenge", "123456");
    final LedgerCommand command =
            new LedgerCommand(
                    "operation-key",
                    7L,
                    10L,
                    "1234567890",
                    "ABCD0001234",
                    "TRANSFER",
                    new BigDecimal("100"),
                    "INR",
                    "Rent");

    PaymentWorkflowStore.Operation operation(String state, String fingerprint) {
        return new PaymentWorkflowStore.Operation(
                1L,
                "TRANSFER",
                "operation-key",
                fingerprint,
                "digest",
                state,
                command,
                99L,
                "TXN-99");
    }

    String fingerprint() {
        return PaymentIntent.transferFingerprint(7L, 10L, 20L, request.amount(), "Rent");
    }

    @Test
    void changedIntentCannotReplayACompletedRequest() {
        when(store.find(7L, "TRANSFER", "request-key"))
                .thenReturn(operation("COMPLETED", "different"));
        assertThatThrownBy(() -> service.transfer(7L, request))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(ledger, otp);
        verify(audit)
                .recordRejected(eq(7L), eq(10L), eq("FUND_TRANSFER"), any(ConflictException.class));
    }

    @Test
    void exactReplayReturnsReceiptWithoutConsumingAnotherOtp() {
        when(store.find(7L, "TRANSFER", "request-key"))
                .thenReturn(operation("COMPLETED", fingerprint()));
        assertThat(service.transfer(7L, request).transactionReference()).isEqualTo("TXN-99");
        verifyNoInteractions(ledger, otp);
    }

    @Test
    void ambiguousTimeoutRemainsAuthorizedAndUsesTheSameLedgerKeyOnRecovery() {
        var pending = operation("AUTHORIZED", fingerprint());
        var receipt = new LedgerReceipt(99L, "TXN-99", "COMPLETED", request.amount(), "INR");
        when(ledger.post(command))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(receipt);
        assertThatThrownBy(() -> service.settle(pending))
                .isInstanceOf(ResponseStatusException.class);
        verify(store, never()).failed(anyString());
        service.settle(pending);
        verify(ledger, times(2)).post(command);
        verify(store).complete("operation-key", receipt);
        verifyNoInteractions(otp);
    }

    @Test
    void definitiveLedgerRejectionFailsTheWorkflow() {
        when(ledger.post(command)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.settle(operation("AUTHORIZED", fingerprint())))
                .isInstanceOf(ResponseStatusException.class);
        verify(store).failed("operation-key");
    }

    @Test
    void authorizationIsPersistedBeforePostingToTheLedger() {
        var pending = operation("AWAITING_OTP", fingerprint());
        when(store.find(7L, "TRANSFER", "request-key")).thenReturn(pending);
        when(store.get("operation-key")).thenReturn(operation("AUTHORIZED", fingerprint()));
        service.transfer(7L, request);
        var ordered = inOrder(otp, store, ledger);
        ordered.verify(otp)
                .authorize("operation-key", 7L, "challenge", "123456", "FUND_TRANSFER", "digest");
        ordered.verify(store).authorized("operation-key");
        ordered.verify(ledger).post(command);
    }
}
