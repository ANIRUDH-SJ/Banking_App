package com.netbanking.card.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netbanking.common.exception.VerificationFailedException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class PinTransportKeysTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);

    public static PinTransportKeys keys(Clock clock) {
        return new PinTransportKeys("", clock);
    }

    /** Encrypts like the browser does: RSA-OAEP, SHA-256, JSON payload with issuedAt. */
    public static String seal(PinTransportKeys keys, String pin, Clock clock) {
        return seal(keys, "{\"pin\":\"" + pin + "\",\"issuedAt\":" + clock.millis() + "}");
    }

    static String seal(PinTransportKeys keys, String payload) {
        try {
            var publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keys.publicKey())));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey,
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            return Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void opensAFreshPinForTheCurrentKey() {
        PinTransportKeys keys = keys(clock);

        assertThat(keys.open(keys.keyId(), seal(keys, "4826", clock), "pin")).isEqualTo("4826");
        assertThat(keys.keyId()).hasSize(16);
    }

    @Test
    void refusesStaleOtherKeyAndMalformedPayloads() {
        PinTransportKeys keys = keys(clock);
        String stale = seal(keys, "{\"pin\":\"4826\",\"issuedAt\":" + (clock.millis() - 6 * 60_000) + "}");

        assertThatThrownBy(() -> keys.open(keys.keyId(), stale, "pin"))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_KEY_EXPIRED"));
        assertThatThrownBy(() -> keys.open("0000000000000000", seal(keys, "4826", clock), "pin"))
                .isInstanceOf(VerificationFailedException.class);
        assertThatThrownBy(() -> keys.open(keys.keyId(), "bm90LWVuY3J5cHRlZA==", "pin"))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_UNREADABLE"));
        assertThatThrownBy(() -> keys.open(keys.keyId(), seal(keys, "48261", clock), "pin"))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_FORMAT"));
    }
}
