package com.netbanking.otp.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.netbanking.discovery.OtpClient;
import com.netbanking.otp.service.OtpAuthorizationService;
import com.netbanking.otp.service.OtpService;
import com.netbanking.user.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class InternalOtpControllerTest {
    private final InternalOtpController controller = new InternalOtpController(
            mock(OtpService.class), mock(UserService.class), mock(OtpAuthorizationService.class));

    @Test
    void limitsDepositOtpToProductsServiceAndPaymentOtpToPaymentsService() {
        String digest = "0".repeat(64);
        var products = new UsernamePasswordAuthenticationToken("products-service", "");
        var payments = new UsernamePasswordAuthenticationToken("payments-service", "");

        assertThatThrownBy(() -> controller.issue(
                new OtpClient.Issue(7L, "FUND_TRANSFER", digest), products))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.issue(
                new OtpClient.Issue(7L, "DEPOSIT_CLOSURE", digest), payments))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.authorize(new OtpClient.Authorization(
                digest, 7L, "challenge", "123456", "DEPOSIT_CLOSURE", digest), payments))
                .isInstanceOf(AccessDeniedException.class);
    }
}
