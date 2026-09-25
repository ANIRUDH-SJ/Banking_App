package com.netbanking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.internal")
public class InternalTokens {
    private Map<String, String> tokenHashes = new HashMap<>();

    public Map<String, String> getTokenHashes() {
        return tokenHashes;
    }

    public void setTokenHashes(Map<String, String> tokenHashes) {
        this.tokenHashes = tokenHashes;
    }

    private static String hash(String token) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public boolean accepts(String caller, String supplied) {
        String expected = tokenHashes.get(caller);
        return expected != null
                && expected.matches("[a-f0-9]{64}")
                && supplied != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        hash(supplied).getBytes(StandardCharsets.UTF_8));
    }
}
