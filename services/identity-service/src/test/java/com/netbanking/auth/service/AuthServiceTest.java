package com.netbanking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netbanking.auth.api.*;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.UnauthorizedException;
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

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository userRepository;
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
    void registrationMapsConcurrentUniqueConstraintFailureToConflict() {
        RegisterRequest request =
                new RegisterRequest("asha", "asha@example.com", "strong-password");
        when(passwordEncoder.encode("strong-password")).thenReturn("password-hash");
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class));

        assertThatThrownBy(() -> service().register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in use");
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
                userService,
                roleService,
                otpService,
                totpService,
                passwordEncoder,
                jwtService);
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
