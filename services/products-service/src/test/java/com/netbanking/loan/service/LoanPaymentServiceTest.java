package com.netbanking.loan.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netbanking.common.exception.*;
import com.netbanking.contracts.*;
import com.netbanking.discovery.*;
import com.netbanking.loan.api.*;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.repository.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

class LoanPaymentServiceTest {
    final LoanRepository loans = mock(LoanRepository.class);
    final LoanPaymentRepository payments = mock(LoanPaymentRepository.class);
    final CustomerDirectory customers = mock(CustomerDirectory.class);
    final LedgerClient ledger = mock(LedgerClient.class);
    final LoanRepaymentStore store = mock(LoanRepaymentStore.class);
    final LoanPaymentService service =
            new LoanPaymentService(loans, payments, customers, ledger, store);
    final CreateLoanPaymentRequest request =
            new CreateLoanPaymentRequest(8L, new BigDecimal("200"), "request-key");
    final LedgerCommand command =
            new LedgerCommand(
                    "operation-key",
                    7L,
                    8L,
                    null,
                    null,
                    "LOAN_PAYMENT",
                    request.amount(),
                    "INR",
                    "Loan payment");

    LoanRepaymentStore.Operation operation(String state, String fingerprint) {
        return new LoanRepaymentStore.Operation(
                "operation-key", 4L, 21L, "request-key", fingerprint, state, command);
    }

    void owned() {
        when(customers.requireCustomerIdForUser(7L)).thenReturn(21L);
        when(loans.findByLoanIdAndCustomerId(4L, 21L)).thenReturn(Optional.of(mock(Loan.class)));
    }

    @Test
    void deniesAnotherCustomersLoanBeforeCallingLedger() {
        when(customers.requireCustomerIdForUser(7L)).thenReturn(21L);
        assertThatThrownBy(() -> service.pay(7L, 4L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ledger, store);
    }

    @Test
    void refusesChangedReplay() {
        owned();
        when(store.find(4L, "request-key")).thenReturn(operation("COMPLETED", "different"));
        assertThatThrownBy(() -> service.pay(7L, 4L, request))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(ledger);
    }

    @Test
    void returnsCompletedReceiptWithoutAnotherDebit() {
        owned();
        var prior =
                operation("COMPLETED", LoanPaymentIntent.fingerprint(7L, 4L, 8L, request.amount()));
        when(store.find(4L, "request-key")).thenReturn(prior);
        service.pay(7L, 4L, request);
        verify(store).receipt(prior);
        verifyNoInteractions(ledger);
    }

    @Test
    void timeoutRetainsReservationAndRecoveryReusesLedgerOperation() {
        var op = operation("RESERVED", "fingerprint");
        var receipt = new LedgerReceipt(55L, "TXN-55", "COMPLETED", request.amount(), "INR");
        when(ledger.post(command))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(receipt);
        assertThatThrownBy(() -> service.settle(op)).isInstanceOf(ResponseStatusException.class);
        verify(store, never()).failed(any());
        service.settle(op);
        verify(ledger, times(2)).post(command);
        verify(store).complete(op, receipt);
    }
}
