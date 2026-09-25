package com.netbanking.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.biller.service.BillerService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.otp.service.OtpService;
import com.netbanking.payment.api.FundTransferRequest;
import com.netbanking.payment.domain.FundTransfer;
import com.netbanking.payment.repository.BillPaymentRepository;
import com.netbanking.payment.repository.FundTransferRepository;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.BankTransactionRepository;
import com.netbanking.transaction.service.TransactionService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock private AccountService accountService;
    @Mock private BankAccountRepository accountRepository;
    @Mock private BeneficiaryService beneficiaryService;
    @Mock private BillerService billerService;
    @Mock private OtpService otpService;
    @Mock private AppUserRepository userRepository;
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private TransactionService transactionService;
    @Mock private FundTransferRepository transferRepository;
    @Mock private BillPaymentRepository billPaymentRepository;
    @Mock private PaymentAuditService paymentAuditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void rejectsAnIdempotencyKeyReusedForDifferentTransferDetails() {
        AppUser user = user();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        String originalFingerprint = PaymentIntent.transferFingerprint(
                7L, 10L, 20L, new BigDecimal("100"), "Rent");
        FundTransfer prior = new FundTransfer(99L, 10L, 20L, 7L, "request-key", originalFingerprint);
        when(transferRepository.findByInitiatedByUserIdAndIdempotencyKey(7L, "request-key"))
                .thenReturn(Optional.of(prior));
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(transaction(new BigDecimal("100"), "Rent")));

        FundTransferRequest changed = new FundTransferRequest(
                10L, 20L, new BigDecimal("101"), "Rent", "request-key", "challenge", "123456");

        assertThatThrownBy(() -> service().transfer(7L, changed))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different transfer");
        verify(paymentAuditService).recordRejected(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("FUND_TRANSFER"),
                org.mockito.ArgumentMatchers.any(ConflictException.class));
        verifyNoInteractions(otpService, accountService);
    }

    @Test
    void safelyRecognizesAndBindsAnExactLegacyReplay() {
        AppUser user = user();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        FundTransfer prior = new FundTransfer(99L, 10L, 20L, 7L, "request-key", null);
        ReflectionTestUtils.setField(prior, "transferId", 55L);
        BankTransaction transaction = transaction(new BigDecimal("100.00"), "Rent");
        when(transferRepository.findByInitiatedByUserIdAndIdempotencyKey(7L, "request-key"))
                .thenReturn(Optional.of(prior));
        when(transactionRepository.findById(99L)).thenReturn(Optional.of(transaction));

        FundTransferRequest replay = new FundTransferRequest(
                10L, 20L, new BigDecimal("100"), " Rent ", "request-key", "ignored", "123456");

        var receipt = service().transfer(7L, replay);

        assertThat(receipt.paymentId()).isEqualTo(55L);
        assertThat(prior.getRequestFingerprint()).hasSize(64);
        verifyNoInteractions(otpService, accountService);
    }

    private PaymentService service() {
        return new PaymentService(accountService, accountRepository, beneficiaryService, billerService,
                otpService, userRepository, transactionRepository, transactionService,
                transferRepository, billPaymentRepository, paymentAuditService, eventPublisher,
                new BigDecimal("100000"));
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }

    private static BankTransaction transaction(BigDecimal amount, String narration) {
        BankTransaction transaction = new BankTransaction(
                "TXN-99", 10L, null, 20L, 7L, TransactionType.TRANSFER,
                amount, "INR", narration);
        ReflectionTestUtils.setField(transaction, "transactionId", 99L);
        return transaction;
    }
}
