package com.netbanking.discovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netbanking.common.exception.VerificationFailedException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OtpClientTest {
    private final ServiceHttpClient http = mock(ServiceHttpClient.class);
    private final OtpClient otp = new OtpClient(http);

    @Test
    void aRejectedCodeIsAFieldErrorNotAnExpiredSession() {
        when(http.post(eq("identity-service"), eq("/internal/otp/authorizations"), any(), eq(Void.class)))
                .thenThrow(new DownstreamRejectedException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "bad", 0));

        assertThatThrownBy(() -> otp.authorize("op", 7L, "challenge", "123456", "FUND_TRANSFER", "a".repeat(64)))
                .isInstanceOfSatisfying(VerificationFailedException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo("OTP_INVALID");
                    org.assertj.core.api.Assertions.assertThat(e.field()).isEqualTo("otpCode");
                    org.assertj.core.api.Assertions.assertThat(e.locked()).isFalse();
                });
    }

    @Test
    void otherFailuresAreRelayedUnchanged() {
        when(http.post(any(), any(), any(), eq(Void.class)))
                .thenThrow(new DownstreamRejectedException(HttpStatus.CONFLICT, "CONFLICT", "x", 0));

        assertThatThrownBy(() -> otp.authorize("op", 7L, "challenge", "123456", "FUND_TRANSFER", "a".repeat(64)))
                .isInstanceOf(DownstreamRejectedException.class);
    }
}
