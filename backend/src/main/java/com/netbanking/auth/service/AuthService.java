package com.netbanking.auth.service;

import com.netbanking.auth.api.*;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.role.service.RoleService;
import com.netbanking.security.JwtService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import com.netbanking.user.service.UserService;
import com.netbanking.totp.api.TotpSetupResponse;
import com.netbanking.totp.service.TotpService;
import java.util.Set;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
    private final AppUserRepository userRepository;
    private final UserService userService;
    private final RoleService roleService;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public AuthService(AppUserRepository userRepository, UserService userService, RoleService roleService,
                       TotpService totpService, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository; this.userService = userService; this.roleService = roleService;
        this.totpService = totpService; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService;
    }
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) throw new ConflictException("Username is already in use.");
        if (userRepository.existsByEmailIgnoreCase(request.email())) throw new ConflictException("Email is already in use.");
        AppUser user = new AppUser(request.username().trim(), request.email().trim().toLowerCase(), passwordEncoder.encode(request.password()));
        roleService.assignDefaultCustomerRole(user);
        userRepository.save(user);
    }
    public LoginChallengeResponse beginLogin(LoginRequest request) {
        AppUser user = userService.requireByUsernameOrEmail(request.usernameOrEmail().trim());
        userService.requireEligibleForLogin(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userService.recordFailedLogin(user);
            throw new UnauthorizedException("Invalid username or password.");
        }
        if (!totpService.isEnabled(user)) {
            return new LoginChallengeResponse(null, "TOTP_SETUP_REQUIRED");
        }
        return new LoginChallengeResponse(jwtService.createTotpLoginChallenge(user), "TOTP_REQUIRED");
    }
    public AuthenticationResponse verifyLoginTotp(LoginTotpVerifyRequest request) {
        AppUser user;
        try {
            user = userService.requireById(jwtService.parseTotpLoginChallenge(request.challengeId()));
        } catch (JwtException | IllegalArgumentException | ResourceNotFoundException exception) {
            throw new UnauthorizedException("Login challenge is invalid or expired.");
        }
        userService.requireEligibleForLogin(user);
        try {
            totpService.verifyLogin(user, request.code());
        } catch (UnauthorizedException exception) {
            userService.recordFailedLogin(user);
            throw exception;
        }
        userService.recordSuccessfulLogin(user);
        Set<String> roles = user.getRoles().stream().map(role -> role.getRoleCode()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticationResponse(jwtService.createToken(user), "Bearer", user.getUserId(), user.getUsername(), roles);
    }
    public TotpSetupResponse beginTotpSetup(LoginRequest request) {
        AppUser user = verifyCredentials(request);
        return totpService.beginSetup(user);
    }
    public void confirmTotpSetup(com.netbanking.totp.api.TotpConfirmRequest request) {
        AppUser user = verifyCredentials(request.credentials());
        totpService.confirmSetup(user, request.code());
    }
    private AppUser verifyCredentials(LoginRequest request) {
        AppUser user = userService.requireByUsernameOrEmail(request.usernameOrEmail().trim());
        userService.requireEligibleForLogin(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw new UnauthorizedException("Invalid username or password.");
        return user;
    }
}
