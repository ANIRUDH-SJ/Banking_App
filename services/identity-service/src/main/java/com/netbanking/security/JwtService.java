package com.netbanking.security;

import com.netbanking.user.domain.AppUser;

import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final RSAPrivateKey key;
    private final JwtVerifier verifier;
    private final String issuer;
    private final Duration expiration, challengeExpiration;

    public JwtService(
            @Value("${app.security.jwt.private-key}") String encoded,
            JwtVerifier verifier,
            @Value("${app.security.jwt.issuer:banking-identity}") String issuer,
            @Value("${app.security.jwt.expiration-minutes:15}") long minutes,
            @Value("${app.security.totp.login-challenge-minutes:5}") long challengeMinutes) {
        try {
            key =
                    (RSAPrivateKey)
                            KeyFactory.getInstance("RSA")
                                    .generatePrivate(
                                            new PKCS8EncodedKeySpec(
                                                    Base64.getDecoder().decode(encoded)));
            if (key.getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("RSA key is too short");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Configure a Base64 PKCS8 RSA private key of at least 2048 bits.", e);
        }
        this.verifier = verifier;
        this.issuer = issuer;
        expiration = Duration.ofMinutes(minutes);
        challengeExpiration = Duration.ofMinutes(challengeMinutes);
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience()
                .add("banking-api")
                .and()
                .subject(user.getUserId().toString())
                .claim("username", user.getUsername())
                .claim(
                        "roles",
                        user.getRoles().stream().map(r -> r.getRoleCode()).sorted().toList())
                .claim("token_use", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    public String createTotpLoginChallenge(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience()
                .add("banking-login")
                .and()
                .subject(user.getUserId().toString())
                .claim("token_use", "totp_login")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(challengeExpiration)))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    public Long parseTotpLoginChallenge(String token) {
        return Long.valueOf(verifier.claims(token, "totp_login", "banking-login").getSubject());
    }
}
