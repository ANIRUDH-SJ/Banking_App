package com.netbanking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.auth.api.LoginRequest;
import com.netbanking.auth.api.LoginTotpVerifyRequest;
import com.netbanking.auth.api.RegisterRequest;
import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.contracts.CustomerRegistered;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.events.EventOutbox;
import com.netbanking.role.service.RoleService;
import com.netbanking.security.JwtService;
import com.netbanking.totp.service.TotpService;
import com.netbanking.user.domain.AppUser;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private EventOutbox eventOutbox;
    @Mock private IdentityAuditService audit;

    @Mock private UserService userService;
    @Mock private RoleService roleService;
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

        var response = service().beginLogin(new LoginRequest("asha", "password"));

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
                service().verifyLoginTotp(new LoginTotpVerifyRequest("login-challenge", "123456"));

        verify(totpService).verifyLogin(user, "123456");
        verify(userService).recordSuccessfulLogin(user);
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
                                                        "login-challenge", "000000")))
                .isInstanceOf(UnauthorizedException.class);
        verify(userService).recordFailedLogin(7L);
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
    void unknownLoginPrincipalIsAuditedAsDenied() {
        when(userService.requireByUsernameOrEmail("missing"))
                .thenThrow(new UnauthorizedException("Invalid credentials."));

        assertThatThrownBy(() -> service().beginLogin(new LoginRequest("missing", "password")))
                .isInstanceOf(UnauthorizedException.class);

        verify(audit).denied(null, "LOGIN_REJECTED", "USER", null, "unknownPrincipal");

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

    private AuthService service() {
        return new AuthService(
                userRepository,
                customerRepository,
                eventOutbox,
                audit,

                userService,
                roleService,
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

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
