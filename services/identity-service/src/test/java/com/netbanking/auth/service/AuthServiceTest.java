package com.netbanking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netbanking.auth.api.*;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.contracts.CustomerRegistered;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.events.EventOutbox;
import com.netbanking.loginaudit.service.LoginAttemptContext;
import com.netbanking.loginaudit.service.LoginAuditService;
import com.netbanking.contracts.RequestFingerprint;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpIssueLimitException;
import com.netbanking.otp.service.OtpService;

import com.netbanking.role.service.RoleService;
import com.netbanking.security.JwtService;
import com.netbanking.totp.service.TotpService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;
import com.netbanking.user.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private EventOutbox eventOutbox;
    @Mock private LoginAuditService loginAudit;
    @Mock private UserService userService;
    @Mock private RoleService roleService;
    @Mock private OtpService otpService;
    @Mock private TotpService totpService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @Test
    void passwordStepReturnsChallengeBoundToTheVerifiedUser() {
        AppUser user = user();
        when(userService.requireByUsernameOrEmail("asha")).thenReturn(user);
        when(passwordEncoder.matches("password", "password-hash")).thenReturn(true);
        when(totpService.isEnabled(user)).thenReturn(true);
        when(jwtService.createTotpLoginChallenge(user)).thenReturn("login-challenge");

        var response = service().beginLogin(new LoginRequest("asha", "password"), attempt());

        assertThat(response.status()).isEqualTo("TOTP_REQUIRED");
        assertThat(response.challengeId()).isEqualTo("login-challenge");
    }

    @Test
    void validChallengeAndAuthenticatorCodeIssueAccessToken() {
        AppUser user = user();
        when(jwtService.parseTotpLoginChallenge("login-challenge")).thenReturn(7L);
        when(userService.requireById(7L)).thenReturn(user);
        when(jwtService.createToken(user)).thenReturn("access-token");

        var response =
                service()
                        .verifyLoginTotp(
                                new LoginTotpVerifyRequest("login-challenge", "123456"), attempt());

        verify(totpService).verifyLogin(user, "123456");
        verify(userService).recordSuccessfulLogin(user);
        verify(loginAudit).success(user, attempt());
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void invalidAuthenticatorCodeCountsAsFailedLogin() {
        AppUser user = user();
        when(jwtService.parseTotpLoginChallenge("login-challenge")).thenReturn(7L);
        when(userService.requireById(7L)).thenReturn(user);
        org.mockito.Mockito.doThrow(new UnauthorizedException("Authenticator code is invalid."))
                .when(totpService)
                .verifyLogin(user, "000000");

        assertThatThrownBy(
                        () ->
                                service()
                                        .verifyLoginTotp(
                                                new LoginTotpVerifyRequest(
                                                        "login-challenge", "000000"),
                                                attempt()))
                .isInstanceOf(UnauthorizedException.class);
        verify(userService).recordFailedLogin(7L);
        verify(loginAudit).failure(user, "asha", "INVALID_TOTP", attempt());
    }

    @Test
    void invalidPasswordDuringAuthenticatorSetupCountsAsFailedLogin() {
        AppUser user = user();
        when(userService.requireByUsernameOrEmail("asha")).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        assertThatThrownBy(
                        () -> service().beginTotpSetup(new LoginRequest("asha", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);

        verify(userService).recordFailedLogin(7L);
    }

    @Test
    void registrationCreatesCustomerProfileAndPublishesOnboardingEvent() {
        RegisterRequest request = registrationRequest();
        when(passwordEncoder.encode("strong-password")).thenReturn("password-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(
                        invocation -> {
                            AppUser saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "userId", 42L);
                            return saved;
                        });
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(
                        invocation -> {
                            Customer saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "customerId", 84L);
                            return saved;
                        });

        var response = service().register(request);

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.customerId()).isEqualTo(84L);
        assertThat(response.customerNumber()).isEqualTo("CUST0000000000000042");
        verify(eventOutbox)
                .publish(
                        eq("accounts-ledger-service"),
                        eq("CUSTOMER_REGISTERED"),
                        eq(new CustomerRegistered(84L, "CUST0000000000000042")));
    }

    @Test
    void registrationMapsConcurrentUniqueConstraintFailureToConflict() {
        RegisterRequest request = registrationRequest();
        when(passwordEncoder.encode("strong-password")).thenReturn("password-hash");
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class));

        assertThatThrownBy(() -> service().register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void unknownLoginPrincipalCreatesFailedLoginAudit() {
        when(userService.requireByUsernameOrEmail("missing"))
                .thenThrow(new UnauthorizedException("Invalid credentials."));

        assertThatThrownBy(
                        () ->
                                service()
                                        .beginLogin(
                                                new LoginRequest("missing", "password"), attempt()))
                .isInstanceOf(UnauthorizedException.class);

        verify(loginAudit).failure(null, "missing", "UNKNOWN_PRINCIPAL", attempt());
    }

    @Test
    void passwordResetChallengeUsesAGenericResponseForAnExistingAccount() {
        AppUser user = user();
        when(userRepository.findByUsernameIgnoreCase("asha")).thenReturn(Optional.of(user));
        when(otpService.issue(
                        user,
                        OtpPurpose.PASSWORD_RESET,
                        RequestFingerprint.of("PASSWORD_RESET", 7L)))
                .thenReturn(new OtpService.Challenge("reset-challenge"));

        var response =
                service().beginPasswordReset(new PasswordResetChallengeRequest(" asha "));

        assertThat(response.challengeId()).isEqualTo("reset-challenge");
        assertThat(response.status()).isEqualTo("OTP_SENT_IF_ACCOUNT_EXISTS");
    }

    @Test
    void passwordResetChallengeDoesNotRevealAnUnknownAccount() {
        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("missing")).thenReturn(Optional.empty());

        var response =
                service().beginPasswordReset(new PasswordResetChallengeRequest("missing"));

        assertThat(response.challengeId()).isNotBlank();
        assertThat(response.status()).isEqualTo("OTP_SENT_IF_ACCOUNT_EXISTS");
        verifyNoInteractions(otpService);
    }

    @Test
    void passwordResetRateLimitKeepsTheGenericPublicResponse() {
        AppUser user = user();
        when(userRepository.findByUsernameIgnoreCase("asha")).thenReturn(Optional.of(user));
        when(otpService.issue(
                        user,
                        OtpPurpose.PASSWORD_RESET,
                        RequestFingerprint.of("PASSWORD_RESET", 7L)))
                .thenThrow(new OtpIssueLimitException("Too many OTP challenges"));

        var response =
                service().beginPasswordReset(new PasswordResetChallengeRequest("asha"));

        assertThat(response.challengeId()).isNotBlank();
        assertThat(response.status()).isEqualTo("OTP_SENT_IF_ACCOUNT_EXISTS");
    }

    @Test
    void verifiedResetChangesThePasswordAndUnlocksTheAccount() {
        AppUser user = user();
        ReflectionTestUtils.setField(user, "accountStatus", UserStatus.LOCKED);
        when(otpService.verifyPasswordReset("reset-challenge", "123456")).thenReturn(7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-strong-password", "password-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-strong-password")).thenReturn("new-password-hash");

        service()
                .confirmPasswordReset(
                        new PasswordResetConfirmRequest(
                                "reset-challenge", "123456", "new-strong-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
        assertThat(user.getAccountStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void resetRejectsTheCurrentPassword() {
        AppUser user = user();
        when(otpService.verifyPasswordReset("reset-challenge", "123456")).thenReturn(7L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("strong-password", "password-hash")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service()
                                        .confirmPasswordReset(
                                                new PasswordResetConfirmRequest(
                                                        "reset-challenge",
                                                        "123456",
                                                        "strong-password")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("differ");
    }

    private AuthService service() {
        return new AuthService(
                userRepository,
                customerRepository,
                eventOutbox,
                loginAudit,
                userService,
                roleService,
                otpService,
                totpService,
                passwordEncoder,
                jwtService);

    }

    private static RegisterRequest registrationRequest() {
        return new RegisterRequest(
                "asha",
                "asha@example.com",
                "strong-password",
                "Asha",
                "Patil",
                LocalDate.of(1998, 1, 1),
                "9999999999");

    }

    private static LoginAttemptContext attempt() {
        return new LoginAttemptContext("203.0.113.8", "test-agent");
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
