package com.netbanking.totp.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TotpSecretCipher {
    private static final int IV_LENGTH = 12;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public TotpSecretCipher(@Value("${app.security.totp.encryption-key}") String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
        if (key.length != 32) throw new IllegalStateException("app.security.totp.encryption-key must be a Base64-encoded 32-byte key.");
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = Arrays.copyOf(iv, iv.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) { throw new IllegalStateException("Unable to encrypt TOTP secret.", exception); }
    }
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalStateException("Unable to decrypt TOTP secret.", exception); }
    }
}
