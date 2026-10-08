package com.netbanking.card.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class CardNumberCipherTest {
    private final CardNumberCipher cipher =
            new CardNumberCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsTheNumberAndBindsItToTheCardToken() {
        String sealed = cipher.encrypt("4319401234567890", "token-a");

        assertThat(sealed).startsWith("v1:").doesNotContain("4319401234567890");
        assertThat(cipher.decrypt(sealed, "token-a")).isEqualTo("4319401234567890");
        assertThatThrownBy(() -> cipher.decrypt(sealed, "token-b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isDisabledWithoutAKey() {
        CardNumberCipher disabled = new CardNumberCipher("");

        assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(() -> disabled.encrypt("4319401234567890", "token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
