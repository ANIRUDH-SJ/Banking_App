package com.netbanking.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.VerificationFailedException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CardPinClientTest {
    private final ServiceHttpClient http = mock(ServiceHttpClient.class);
    private final CardPinClient pins = new CardPinClient(http);
    private final CardPinClient.Verification verification =
            new CardPinClient.Verification(7L, 5L, 10L, "key", "sealed", "FUND_TRANSFER");

    @Test
    void relaysTheWrongPinMessageAndTheLock() {
        when(http.post(eq("products-service"), any(), any(), eq(Void.class)))
                .thenThrow(new DownstreamRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "PIN_INCORRECT",
                        "Incorrect PIN. 2 attempts left before the PIN is locked.", 0))
                .thenThrow(new DownstreamRejectedException(HttpStatus.LOCKED, "PIN_LOCKED", "Locked.", 1800));

        assertThatThrownBy(() -> pins.verify(verification))
                .isInstanceOfSatisfying(VerificationFailedException.class, e -> {
                    assertThat(e.getMessage()).contains("2 attempts left");
                    assertThat(e.field()).isEqualTo("pin");
                    assertThat(e.locked()).isFalse();
                });
        assertThatThrownBy(() -> pins.verify(verification))
                .isInstanceOfSatisfying(VerificationFailedException.class, e -> {
                    assertThat(e.locked()).isTrue();
                    assertThat(e.retryAfterSeconds()).isEqualTo(1800);
                });
    }

    @Test
    void relaysWhyACardCannotAuthorize() {
        when(http.post(eq("products-service"), any(), any(), eq(Void.class)))
                .thenThrow(new DownstreamRejectedException(HttpStatus.CONFLICT, "CONFLICT",
                        "Use the PIN of the debit card linked to the paying account.", 0));

        assertThatThrownBy(() -> pins.verify(verification))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("debit card linked");
    }
}
