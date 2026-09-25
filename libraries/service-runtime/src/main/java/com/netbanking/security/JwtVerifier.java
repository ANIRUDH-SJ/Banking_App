package com.netbanking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Component
public class JwtVerifier {
    private final RSAPublicKey key;
    private final String issuer;

    public JwtVerifier(
            @Value("${app.security.jwt.public-key}") String encoded,
            @Value("${app.security.jwt.issuer:banking-identity}") String issuer) {
        try {
            key =
                    (RSAPublicKey)
                            KeyFactory.getInstance("RSA")
                                    .generatePublic(
                                            new X509EncodedKeySpec(
                                                    Base64.getDecoder().decode(encoded)));
            if (key.getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("RSA key is too short");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Configure a Base64 X.509 RSA public key of at least 2048 bits.", e);
        }
        this.issuer = issuer;
    }

    public Claims claims(String token, String use, String audience) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(issuer)
                        .requireAudience(audience)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        if (!use.equals(claims.get("token_use", String.class)))
            throw new JwtException("Incorrect token purpose.");
        return claims;
    }

    public JwtPrincipal parse(String token) {
        Claims c = claims(token, "access", "banking-api");
        List<?> raw = c.get("roles", List.class);
        List<String> roles =
                raw == null ? List.of() : raw.stream().map(String.class::cast).toList();
        return new JwtPrincipal(
                Long.valueOf(c.getSubject()), c.get("username", String.class), roles);
    }

    public record JwtPrincipal(Long userId, String username, List<String> roles) {}
}
