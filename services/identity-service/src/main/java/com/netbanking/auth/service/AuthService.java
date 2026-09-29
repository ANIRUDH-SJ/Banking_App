package com.netbanking.auth.service;

import com.netbanking.auth.api.*;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.loginaudit.service.LoginAttemptContext;
import com.netbanking.loginaudit.service.LoginAuditService;
import com.netbanking.role.service.RoleService;
import com.netbanking.security.JwtService;
import com.netbanking.totp.api.TotpSetupResponse;
import com.netbanking.totp.service.TotpService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import com.netbanking.user.service.UserService;

import io.jsonwebtoken.JwtException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class AuthService {
    private final AppUserRepository userRepository;
    private final LoginAuditService loginAudit;
    private final UserService userService;
    private final RoleService roleService;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            LoginAuditService loginAudit,
            UserService userService,
            RoleService roleService,
            TotpService totpService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.loginAudit = loginAudit;
        this.userService = userService;
        this.roleService = roleService;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username))
            throw new ConflictException("Username is already in use.");
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new ConflictException("Email is already in use.");
        AppUser user = new AppUser(username, email, passwordEncoder.encode(request.password()));
        roleService.assignDefaultCustomerRole(user);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Username or email is already in use.");
        }
    }

    public LoginChallengeResponse beginLogin(
            LoginRequest request, LoginAttemptContext attemptContext) {
        String attemptedUsername = request.usernameOrEmail().trim();
        AppUser user;
        try {
            user = userService.requireByUsernameOrEmail(attemptedUsername);
        } catch (UnauthorizedException exception) {
            loginAudit.failure(null, attemptedUsername, "UNKNOWN_PRINCIPAL", attemptContext);
            throw exception;
        }
        try {
            userService.requireEligibleForLogin(user);
        } catch (UnauthorizedException exception) {
            loginAudit.failure(user, attemptedUsername, "ACCOUNT_NOT_ELIGIBLE", attemptContext);
            throw exception;
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userService.recordFailedLogin(user.getUserId());
            loginAudit.failure(user, attemptedUsername, "INVALID_CREDENTIALS", attemptContext);
            throw new UnauthorizedException("Invalid username or password.");
        }
        if (!totpService.isEnabled(user)) {
            return new LoginChallengeResponse(null, "TOTP_SETUP_REQUIRED");
        }
        return new LoginChallengeResponse(
                jwtService.createTotpLoginChallenge(user), "TOTP_REQUIRED");
    }

    public AuthenticationResponse verifyLoginTotp(
            LoginTotpVerifyRequest request, LoginAttemptContext attemptContext) {
        AppUser user;
        try {
            user =
                    userService.requireById(
                            jwtService.parseTotpLoginChallenge(request.challengeId()));
        } catch (JwtException | IllegalArgumentException | ResourceNotFoundException exception) {
            loginAudit.failure(null, "challenge", "INVALID_CHALLENGE", attemptContext);
            throw new UnauthorizedException("Login challenge is invalid or expired.");
        }
        try {
            userService.requireEligibleForLogin(user);
        } catch (UnauthorizedException exception) {
            loginAudit.failure(
                    user, user.getUsername(), "ACCOUNT_NOT_ELIGIBLE", attemptContext);
            throw exception;
        }
        try {
            totpService.verifyLogin(user, request.code());
        } catch (UnauthorizedException exception) {
            userService.recordFailedLogin(user.getUserId());
            loginAudit.failure(user, user.getUsername(), "INVALID_TOTP", attemptContext);
            throw exception;
        }
        userService.recordSuccessfulLogin(user);
        Set<String> roles =
                user.getRoles().stream()
                        .map(role -> role.getRoleCode())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        AuthenticationResponse response =
                new AuthenticationResponse(
                        jwtService.createToken(user),
                        "Bearer",
                        user.getUserId(),
                        user.getUsername(),
                        roles);
        loginAudit.success(user, attemptContext);
        return response;
    }

    public TotpSetupResponse beginTotpSetup(LoginRequest request) {
        AppUser user = verifyCredentials(request);
        return totpService.beginSetup(user);
    }

    public void confirmTotpSetup(com.netbanking.totp.api.TotpConfirmRequest request) {
        AppUser user = verifyCredentials(request.credentials());
        try {
            totpService.confirmSetup(user, request.code());
        } catch (UnauthorizedException exception) {
            userService.recordFailedLogin(user.getUserId());
            throw exception;
        }
    }

    private AppUser verifyCredentials(LoginRequest request) {
        AppUser user = userService.requireByUsernameOrEmail(request.usernameOrEmail().trim());
        userService.requireEligibleForLogin(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userService.recordFailedLogin(user.getUserId());
            throw new UnauthorizedException("Invalid username or password.");
        }
        return user;
    }
}
