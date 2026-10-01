package com.netbanking.auth.api;

import com.netbanking.auth.service.AuthService;
import com.netbanking.loginaudit.service.LoginAttemptContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginChallengeResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.beginLogin(request, LoginAttemptContext.from(httpRequest));
    }

    @PostMapping("/login/verify-totp")
    public AuthenticationResponse verifyTotp(
            @Valid @RequestBody LoginTotpVerifyRequest request, HttpServletRequest httpRequest) {
        return authService.verifyLoginTotp(request, LoginAttemptContext.from(httpRequest));
    }

    @PostMapping("/password-reset/challenges")
    public ResponseEntity<PasswordResetChallengeResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetChallengeRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.beginPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
    }

    @PostMapping("/totp/setup")
    public ResponseEntity<com.netbanking.totp.api.TotpSetupResponse> setupTotp(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.beginTotpSetup(request));
    }

    @PostMapping("/totp/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmTotp(
            @Valid @RequestBody com.netbanking.totp.api.TotpConfirmRequest request) {
        authService.confirmTotpSetup(request);
    }
}
