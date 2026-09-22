package com.netbanking.auth.api;
import com.netbanking.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) { authService.register(request); }
    @PostMapping("/login")
    public LoginChallengeResponse login(@Valid @RequestBody LoginRequest request) { return authService.beginLogin(request); }
    @PostMapping("/login/verify-totp")
    public AuthenticationResponse verifyTotp(@Valid @RequestBody LoginTotpVerifyRequest request) { return authService.verifyLoginTotp(request); }
    @PostMapping("/totp/setup")
    public com.netbanking.totp.api.TotpSetupResponse setupTotp(@Valid @RequestBody LoginRequest request) { return authService.beginTotpSetup(request); }
    @PostMapping("/totp/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmTotp(@Valid @RequestBody com.netbanking.totp.api.TotpConfirmRequest request) { authService.confirmTotpSetup(request); }
}
