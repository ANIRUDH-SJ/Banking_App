package com.netbanking.beneficiary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netbanking.beneficiary.api.BeneficiaryActivationRequest;
import com.netbanking.beneficiary.domain.Beneficiary;
import com.netbanking.beneficiary.repository.BeneficiaryRepository;
import com.netbanking.customer.service.CustomerService;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {
    @Mock private BeneficiaryRepository repository;
    @Mock private CustomerService customerService;
    @Mock private OtpService otpService;
    @Mock private AppUserRepository userRepository;

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DISABLED"})
    void rejectsActivationChallengeForNonPendingBeneficiary(String status) {
        stubOwned(beneficiaryWithStatus(status));

        assertThatThrownBy(() -> service().issueActivationOtp(7L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending beneficiary");

        verifyNoInteractions(userRepository, otpService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "DISABLED"})
    void doesNotConsumeAnOtpForNonPendingBeneficiary(String status) {
        stubOwned(beneficiaryWithStatus(status));

        assertThatThrownBy(() -> service().activate(7L, 3L,
                new BeneficiaryActivationRequest("challenge-1", "123456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending beneficiary");

        verifyNoInteractions(otpService);
    }

    @Test
    void pendingBeneficiaryCanReceiveAndUseActivationChallengeAfterCoolingPeriod() {
        Beneficiary beneficiary = beneficiaryWithStatus("PENDING");
        stubOwned(beneficiary);
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(otpService.issue(eq(user), eq(OtpPurpose.BENEFICIARY_ACTIVATION), anyString()))
                .thenReturn(new OtpService.Challenge("challenge-1"));

        var challenge = service().issueActivationOtp(7L, 3L);
        var activated = service().activate(7L, 3L,
                new BeneficiaryActivationRequest(challenge.challengeId(), "123456"));

        assertThat(challenge.status()).isEqualTo("OTP_SENT");
        assertThat(activated.status()).isEqualTo("ACTIVE");
        verify(otpService).verifyForUser(eq(7L), eq("challenge-1"), eq("123456"),
                eq(OtpPurpose.BENEFICIARY_ACTIVATION), anyString());
    }

    private void stubOwned(Beneficiary beneficiary) {
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(11L);
        when(repository.findByBeneficiaryIdAndCustomerId(3L, 11L)).thenReturn(Optional.of(beneficiary));
    }

    private static Beneficiary beneficiaryWithStatus(String status) {
        Beneficiary beneficiary = new Beneficiary(11L, "Rent", "Landlord",
                "123456789012", "ABCD0001234", "Example Bank");
        ReflectionTestUtils.setField(beneficiary, "createdAt",
                Instant.now().minus(Duration.ofHours(1)));
        if ("ACTIVE".equals(status)) beneficiary.activate();
        if ("DISABLED".equals(status)) beneficiary.disable();
        return beneficiary;
    }

    private BeneficiaryService service() {
        return new BeneficiaryService(repository, customerService, otpService, userRepository, 30);
    }
}
