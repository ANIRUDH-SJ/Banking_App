package com.netbanking.auth.service;

import com.netbanking.auth.api.*;
import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.contracts.CustomerRegistered;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.events.EventOutbox;
import com.netbanking.contracts.RequestFingerprint;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpIssueLimitException;
import com.netbanking.otp.service.OtpService;

import com.netbanking.role.service.RoleService;
import com.netbanking.security.JwtService;
import com.netbanking.totp.api.TotpSetupResponse;
import com.netbanking.totp.service.TotpService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;
import com.netbanking.user.service.UserService;

import io.jsonwebtoken.JwtException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AuthService {
    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final EventOutbox eventOutbox;
    private final IdentityAuditService audit;

    private final UserService userService;
    private final RoleService roleService;
    private final OtpService otpService;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            CustomerRepository customerRepository,
            EventOutbox eventOutbox,
            IdentityAuditService audit,

            UserService userService,
            RoleService roleService,
            OtpService otpService,
            TotpService totpService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.eventOutbox = eventOutbox;
        this.audit = audit;

        this.userService = userService;
        this.roleService = roleService;
        this.otpService = otpService;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegistrationResponse register(RegisterRequest request) {
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
            Customer customer =
                    customerRepository.saveAndFlush(
                            Customer.create(
                                    user.getUserId(),
                                    customerNumber(user.getUserId()),
                                    request.firstName().trim(),
                                    request.lastName().trim(),
                                    request.dateOfBirth(),
                                    normalizeMobileNumber(request.mobileNumber())));
            eventOutbox.publish(
                    "accounts-ledger-service",
                    "CUSTOMER_REGISTERED",
                    new CustomerRegistered(
                            customer.getCustomerId(), customer.getCustomerNumber()));
            return new RegistrationResponse(
                    user.getUserId(),
                    customer.getCustomerId(),
                    customer.getCustomerNumber(),
                    "ACTIVE");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Registration details are already in use.");
        }
    }

    private static String customerNumber(Long userId) {
        return "CUST" + String.format(Locale.ROOT, "%016d", userId);
    }

    private static String normalizeMobileNumber(String mobileNumber) {
        return mobileNumber.replace(" ", "").replace("-", "");
    }

    public LoginChallengeResponse beginLogin(LoginRequest request) {
        AppUser user;
        try {
            user = userService.requireByUsernameOrEmail(request.usernameOrEmail().trim());
        } catch (UnauthorizedException exception) {
            audit.denied(null, "LOGIN_REJECTED", "USER", null, "unknownPrincipal");
            throw exception;
        }
        userService.requireEligibleForLogin(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userService.recordFailedLogin(user.getUserId());
            throw new UnauthorizedException("Invalid username or password.");
        }
        if (!totpService.isEnabled(user)) {
            return new LoginChallengeResponse(null, "TOTP_SETUP_REQUIRED");
        }
        return new LoginChallengeResponse(
                jwtService.createTotpLoginChallenge(user), "TOTP_REQUIRED");
    }

    public AuthenticationResponse verifyLoginTotp(LoginTotpVerifyRequest request) {
        AppUser user;
        try {
            user =
                    userService.requireById(
                            jwtService.parseTotpLoginChallenge(request.challengeId()));
        } catch (JwtException | IllegalArgumentException | ResourceNotFoundException exception) {
            throw new UnauthorizedException("Login challenge is invalid or expired.");
        }
        userService.requireEligibleForLogin(user);
        try {
            totpService.verifyLogin(user, request.code());
        } catch (UnauthorizedException exception) {
            userService.recordFailedLogin(user.getUserId());
            throw exception;
        }
        audit.success(user.getUserId(), "TOTP_ENABLED", "USER", String.valueOf(user.getUserId()));
        userService.recordSuccessfulLogin(user);
        Set<String> roles =
                user.getRoles().stream()
                        .map(role -> role.getRoleCode())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticationResponse(
                jwtService.createToken(user),
                "Bearer",
                user.getUserId(),
                user.getUsername(),
                roles);
    }

    public PasswordResetChallengeResponse beginPasswordReset(
            PasswordResetChallengeRequest request) {
        String identifier = request.usernameOrEmail().strip();
        AppUser user =
                userRepository
                        .findByUsernameIgnoreCase(identifier)
                        .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                        .filter(
                                candidate ->
                                        candidate.getAccountStatus() == UserStatus.ACTIVE
                                                || candidate.getAccountStatus()
                                                        == UserStatus.LOCKED)
                        .orElse(null);
        String challengeId = UUID.randomUUID().toString();
        if (user != null) {
            try {
                challengeId =
                        otpService
                                .issue(
                                        user,
                                        OtpPurpose.PASSWORD_RESET,
                                        RequestFingerprint.of(
                                                "PASSWORD_RESET", user.getUserId()))
                                .challengeId();
            } catch (OtpIssueLimitException ignored) {
                // Preserve the same public response for existing and unknown accounts.
            }
        }
        return new PasswordResetChallengeResponse(
                challengeId, "OTP_SENT_IF_ACCOUNT_EXISTS");
    }

    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Long userId = otpService.verifyPasswordReset(request.challengeId(), request.code());
        AppUser user =
                userRepository
                        .findByIdForUpdate(userId)
                        .filter(
                                candidate ->
                                        candidate.getAccountStatus() == UserStatus.ACTIVE
                                                || candidate.getAccountStatus()
                                                        == UserStatus.LOCKED)
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                "Password reset challenge is invalid or expired."));
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException("New password must differ from the current password.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.unlock();
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
