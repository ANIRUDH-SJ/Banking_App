package com.netbanking.security;

import com.netbanking.user.domain.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration expiration;
    private final Duration loginChallengeExpiration;

    public JwtService(@Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes,
                      @Value("${app.security.totp.login-challenge-minutes:5}") long loginChallengeMinutes) {
        if (secret == null || secret.startsWith("REPLACE_")) {
            throw new IllegalStateException("A Base64-encoded app.security.jwt.secret must be configured.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.security.jwt.secret must be Base64-encoded.", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must decode to at least 32 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.loginChallengeExpiration = Duration.ofMinutes(loginChallengeMinutes);
    }
    public String createToken(AppUser user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(role -> role.getRoleCode()).sorted().toList();
        return Jwts.builder().subject(user.getUserId().toString()).claim("username", user.getUsername())
                .claim("roles", roles).claim("token_use", "access")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(expiration))).signWith(key).compact();
    }

    public String createTotpLoginChallenge(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getUserId().toString()).claim("token_use", "totp_login")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(loginChallengeExpiration)))
                .signWith(key).compact();
    }

    public JwtPrincipal parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        requireTokenUse(claims.get("token_use", String.class), "access");
        @SuppressWarnings("unchecked") List<String> roles = claims.get("roles", List.class);
        return new JwtPrincipal(Long.valueOf(claims.getSubject()), claims.get("username", String.class), roles == null ? List.of() : roles);
    }

    public Long parseTotpLoginChallenge(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        requireTokenUse(claims.get("token_use", String.class), "totp_login");
        return Long.valueOf(claims.getSubject());
    }

    private static void requireTokenUse(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new JwtException("Token cannot be used for this operation.");
        }
    }

    public record JwtPrincipal(Long userId, String username, List<String> roles) { }
}
