package com.netbanking.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.customer.service.CustomerService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.loan.api.CreateLoanPaymentRequest;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanPayment;
import com.netbanking.loan.domain.LoanStatus;
import com.netbanking.loan.domain.LoanType;
import com.netbanking.loan.repository.LoanPaymentRepository;
import com.netbanking.loan.repository.LoanRepository;
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.service.CreateTransactionCommand;
import com.netbanking.transaction.service.TransactionRecord;
import com.netbanking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanPaymentServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private LoanPaymentRepository paymentRepository;
    @Mock private BankAccountRepository accountRepository;
    @Mock private CustomerService customerService;
    @Mock private AccountService accountService;
    @Mock private TransactionService transactionService;
    @Mock private LoanPaymentAuditWriter auditWriter;
    @Mock private BankAccount account;

    private LoanPaymentService paymentService;
    private Loan loan;

    @BeforeEach
    void setUp() {
        paymentService = new LoanPaymentService(
                loanRepository, paymentRepository, accountRepository, customerService,
                accountService, transactionService, auditWriter);
        loan = loan();
        ReflectionTestUtils.setField(loan, "loanId", 4L);
    }

    @Test
    void completesAnAtomicLoanPayment() {
        CreateLoanPaymentRequest request = request("payment-key-001");
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
        when(loanRepository.findByLoanIdAndCustomerIdForUpdate(4L, 21L))
                .thenReturn(Optional.of(loan));
        when(paymentRepository.findByLoanIdAndIdempotencyKey(4L, "payment-key-001"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(account));
        when(account.getAccountId()).thenReturn(8L);
        when(account.getCurrencyCode()).thenReturn("INR");
        when(account.getCurrentBalance()).thenReturn(new BigDecimal("800.00"));
        when(transactionService.createTransaction(any(CreateTransactionCommand.class)))
                .thenReturn(new TransactionRecord(55L, "TXN-LOAN-55", TransactionType.LOAN_PAYMENT,
                        TransactionStatus.PENDING, new BigDecimal("200.00"), "INR"));
        when(paymentRepository.save(any(LoanPayment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.pay(7L, 4L, request);

        assertThat(response.transactionReference()).isEqualTo("TXN-LOAN-55");
        assertThat(response.outstandingAfter()).isEqualByComparingTo("74800.00");
        verify(accountService).requireOwnership(7L, 8L);
        verify(account).debit(new BigDecimal("200.00"));
        verify(transactionService).postEntry(55L, 8L, EntryType.DEBIT, new BigDecimal("800.00"));
        verify(transactionService).changeStatus(55L, TransactionStatus.PROCESSING, 7L, null);
        verify(transactionService).changeStatus(55L, TransactionStatus.COMPLETED, 7L, null);
        verify(auditWriter).recordCompleted(7L, 4L, "TXN-LOAN-55");

        ArgumentCaptor<CreateTransactionCommand> command =
                ArgumentCaptor.forClass(CreateTransactionCommand.class);
        verify(transactionService).createTransaction(command.capture());
        assertThat(command.getValue().type()).isEqualTo(TransactionType.LOAN_PAYMENT);
        assertThat(command.getValue().debitAccountId()).isEqualTo(8L);
    }

    @Test
    void returnsTheExistingPaymentForARepeatedIdempotencyKey() {
        CreateLoanPaymentRequest request = request("payment-key-001");
        LoanPayment existing = new LoanPayment(
                4L, 8L, 55L, "TXN-LOAN-55", "payment-key-001",
                new BigDecimal("200.00"), "INR", new BigDecimal("74800.00"),
                LoanPaymentIntent.fingerprint(7L, 4L, 8L, new BigDecimal("200.00")));
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
        when(loanRepository.findByLoanIdAndCustomerIdForUpdate(4L, 21L))
                .thenReturn(Optional.of(loan));
        when(paymentRepository.findByLoanIdAndIdempotencyKey(4L, "payment-key-001"))
                .thenReturn(Optional.of(existing));

        var response = paymentService.pay(7L, 4L, request);

        assertThat(response.transactionReference()).isEqualTo("TXN-LOAN-55");
        verifyNoInteractions(accountService, accountRepository, transactionService, auditWriter);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedWithDifferentPaymentDetails() {
        CreateLoanPaymentRequest request = request("payment-key-001");
        LoanPayment existing = new LoanPayment(
                4L, 9L, 55L, "TXN-LOAN-55", "payment-key-001",
                new BigDecimal("100.00"), "INR", new BigDecimal("74900.00"),
                LoanPaymentIntent.fingerprint(7L, 4L, 9L, new BigDecimal("100.00")));
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
        when(loanRepository.findByLoanIdAndCustomerIdForUpdate(4L, 21L))
                .thenReturn(Optional.of(loan));
        when(paymentRepository.findByLoanIdAndIdempotencyKey(4L, "payment-key-001"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.pay(7L, 4L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different loan payment");
        verifyNoInteractions(accountService, accountRepository, transactionService, auditWriter);
    }

    private static CreateLoanPaymentRequest request(String key) {
        return new CreateLoanPaymentRequest(8L, new BigDecimal("200.00"), key);
    }

    private static Loan loan() {
        return new Loan(21L, "LN20260001", LoanType.PERSONAL,
                new BigDecimal("100000.00"), new BigDecimal("75000.00"),
                new BigDecimal("11.5000"), 24, new BigDecimal("4684.00"), "INR",
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 10, 5),
                LocalDate.of(2027, 12, 5), LoanStatus.ACTIVE);
    }
}
