package com.netbanking.otp.api;

import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.discovery.OtpClient.*;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpAuthorizationService;
import com.netbanking.otp.service.OtpService;
import com.netbanking.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/otp")
@PreAuthorize("hasAuthority('SERVICE_payments-service')")
public class InternalOtpController {
    private final OtpService otp;
    private final UserService users;
    private final OtpAuthorizationService authorizations;

    public InternalOtpController(
            OtpService otp, UserService users, OtpAuthorizationService authorizations) {
        this.otp = otp;
        this.users = users;
        this.authorizations = authorizations;
    }

    @PostMapping("/challenges")
    public Challenge issue(@Valid @RequestBody Issue request) {
        var user = users.requireById(request.userId());
        users.requireEligibleForLogin(user);
        return new Challenge(
                otp.issue(user, purpose(request.purpose()), request.intentDigest()).challengeId());
    }

    @PostMapping("/authorizations")
    public void authorize(@Valid @RequestBody Authorization request, Authentication caller) {
        purpose(request.purpose());
        users.requireEligibleForLogin(users.requireById(request.userId()));
        if (!authorizations.authorize(caller.getName(), request))
            throw new UnauthorizedException(
                    "OTP challenge is invalid, expired, or does not match this operation.");
    }

    private static OtpPurpose purpose(String value) {
        var purpose = OtpPurpose.valueOf(value);
        if (purpose != OtpPurpose.FUND_TRANSFER
                && purpose != OtpPurpose.BILL_PAYMENT
                && purpose != OtpPurpose.BENEFICIARY_ACTIVATION)
            throw new IllegalArgumentException("Unsupported authorization purpose.");
        return purpose;
    }
}
