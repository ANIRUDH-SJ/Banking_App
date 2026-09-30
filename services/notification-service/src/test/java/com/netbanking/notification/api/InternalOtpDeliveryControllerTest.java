package com.netbanking.notification.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.netbanking.discovery.OtpDeliveryClient;
import com.netbanking.notification.service.OtpChannelDeliveryService;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;

class InternalOtpDeliveryControllerTest {
    private final OtpChannelDeliveryService delivery = mock(OtpChannelDeliveryService.class);
    private final InternalOtpDeliveryController controller =
            new InternalOtpDeliveryController(delivery);
    private final OtpDeliveryClient.Command command =
            new OtpDeliveryClient.Command(
                    "challenge-1",
                    7L,
                    "asha@example.com",
                    null,
                    "LOGIN",
                    "123456",
                    Instant.parse("2026-09-29T10:30:00Z"));

    @Test
    void acceptsOnlyTheIdentityServiceCaller() {
        var caller = new TestingAuthenticationToken("payments-service", "token");

        assertThatThrownBy(() -> controller.deliver(command, caller))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(delivery);
    }

    @Test
    void delegatesAnAuthenticatedIdentityRequest() {
        var caller = new TestingAuthenticationToken("identity-service", "token");

        controller.deliver(command, caller);

        verify(delivery).deliver(command);
    }
}
