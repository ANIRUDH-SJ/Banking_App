package com.netbanking.discovery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netbanking.common.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EmailDeliveryErrorsTest {
    @Test
    void knownRecipientFailureSurvivesBothOtpServiceBoundariesWithoutLeakingDetails() {
        var http = mock(ServiceHttpClient.class);
        when(http.post(any(), any(), any(), any())).thenThrow(new DownstreamRejectedException(
                HttpStatus.UNPROCESSABLE_ENTITY, "EMAIL_RECIPIENT_UNDELIVERABLE", "private SMTP body", 0));
        assertThatThrownBy(() -> new OtpDeliveryClient(http).deliver(null))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> {
                    assertThat(e.code()).isEqualTo("EMAIL_RECIPIENT_UNDELIVERABLE");
                    assertThat(e.getReason()).contains("demo address").doesNotContain("private SMTP body");
                });
        assertThatThrownBy(() -> new OtpClient(http).issue(7L, "FUND_TRANSFER", "a".repeat(64)))
                .isInstanceOf(EmailDeliveryException.class).hasMessageContaining("demo address");
    }

    @Test
    void knownUnavailableFailureUsesSafeMessageAndUnknownFailuresRemainGeneric() {
        var known = new DownstreamRejectedException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_DELIVERY_UNAVAILABLE", "SMTP credentials", 0);
        assertThat(EmailDeliveryErrors.translate(known)).isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining("SMTP credentials");
        var unknown = new DownstreamRejectedException(HttpStatus.FORBIDDEN, "FORBIDDEN", "private", 0);
        assertThat(EmailDeliveryErrors.translate(unknown)).isSameAs(unknown);
        var wrongStatus = new DownstreamRejectedException(HttpStatus.FORBIDDEN,
                "EMAIL_RECIPIENT_UNDELIVERABLE", "private", 0);
        assertThat(EmailDeliveryErrors.translate(wrongStatus)).isSameAs(wrongStatus);
    }
}
