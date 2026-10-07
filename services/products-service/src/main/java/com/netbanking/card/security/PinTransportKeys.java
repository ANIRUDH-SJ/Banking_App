package com.netbanking.card.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.common.exception.VerificationFailedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * The RSA key browsers use to encrypt a card PIN (RSA-OAEP with SHA-256) before it leaves the
 * device. The encrypted payload is {@code {"pin":"1234","issuedAt":<epoch millis>}}; stale payloads
 * are refused so a captured ciphertext cannot be replayed later. No service other than this one
 * ever sees the PIN in clear.
 */
@Component
public class PinTransportKeys {
    public static final String ALGORITHM = "RSA-OAEP-256";
    private static final Logger log = LoggerFactory.getLogger(PinTransportKeys.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final Duration MAX_SKEW = Duration.ofMinutes(1);

    private final PrivateKey privateKey;
    private final String publicKey;
    private final String keyId;
    private final Clock clock;

    @Autowired
    public PinTransportKeys(@Value("${app.cards.pin-transport-private-key:}") String configured) {
        this(configured, Clock.systemUTC());
    }

    PinTransportKeys(String configured, Clock clock) {
        this.clock = clock;
        try {
            KeyPair pair;
            if (configured == null || configured.isBlank()) {
                log.warn("app.cards.pin-transport-private-key is not set; using a key that changes on restart.");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                pair = generator.generateKeyPair();
            } else {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                PrivateKey key =
                        factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(configured.strip())));
                if (!(key instanceof RSAPrivateCrtKey crt))
                    throw new IllegalArgumentException("The PIN transport key must be an RSA private key.");
                PublicKey pub = factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                pair = new KeyPair(pub, key);
            }
            this.privateKey = pair.getPrivate();
            byte[] encoded = pair.getPublic().getEncoded();
            this.publicKey = Base64.getEncoder().encodeToString(encoded);
            this.keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)).substring(0, 16);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("The PIN transport key could not be loaded.", failure);
        }
    }

    public String keyId() {
        return keyId;
    }

    /** SubjectPublicKeyInfo (SPKI), base64. */
    public String publicKey() {
        return publicKey;
    }

    public long maxAgeSeconds() {
        return MAX_AGE.toSeconds();
    }

    /** Returns the four PIN digits, or throws if the payload is not a fresh PIN for this key. */
    public String open(String suppliedKeyId, String encryptedPin, String field) {
        if (!keyId.equals(suppliedKeyId))
            throw new VerificationFailedException(
                    "PIN_KEY_EXPIRED", field, "Your session's security key changed. Enter the PIN again.");
        JsonNode payload;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    privateKey,
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            payload = JSON.readTree(cipher.doFinal(Base64.getDecoder().decode(encryptedPin)));
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException unreadable) {
            throw new VerificationFailedException(
                    "PIN_UNREADABLE", field, "The PIN could not be read. Enter it again.");
        }
        long issuedAt = payload.path("issuedAt").asLong(0);
        long now = clock.millis();
        if (issuedAt < now - MAX_AGE.toMillis() || issuedAt > now + MAX_SKEW.toMillis())
            throw new VerificationFailedException(
                    "PIN_KEY_EXPIRED", field, "This PIN entry has expired. Enter the PIN again.");
        String pin = payload.path("pin").asText("");
        if (!pin.matches("[0-9]{4}"))
            throw new VerificationFailedException("PIN_FORMAT", field, "A card PIN has exactly four digits.");
        return pin;
    }
}
