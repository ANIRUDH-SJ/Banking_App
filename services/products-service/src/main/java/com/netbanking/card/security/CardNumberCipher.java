package com.netbanking.card.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts full card numbers with AES-256-GCM. The card token is the associated data, so a
 * ciphertext copied onto another card row does not decrypt. Without a configured key, card numbers
 * are not stored and cannot be revealed.
 */
@Component
public class CardNumberCipher {
    private static final Logger log = LoggerFactory.getLogger(CardNumberCipher.class);
    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CardNumberCipher(@Value("${app.cards.pan-encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            log.warn("app.cards.pan-encryption-key is not set; card numbers cannot be revealed.");
            return;
        }
        byte[] raw = Base64.getDecoder().decode(encodedKey.strip());
        if (raw.length != 32)
            throw new IllegalArgumentException("The card number encryption key must be 32 bytes (base64).");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean enabled() {
        return key != null;
    }

    public String encrypt(String cardNumber, String cardToken) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(cardToken.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(cardNumber.getBytes(StandardCharsets.US_ASCII));
            return VERSION
                    + Base64.getEncoder()
                            .encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("The card number could not be protected.", failure);
        }
    }

    public String decrypt(String ciphertext, String cardToken) {
        requireKey();
        if (ciphertext == null || !ciphertext.startsWith(VERSION))
            throw new IllegalStateException("The card number is not available.");
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            cipher.updateAAD(cardToken.getBytes(StandardCharsets.UTF_8));
            return new String(
                    cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IllegalStateException("The card number is not available.");
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("Card number reveal is not configured.");
    }
}
