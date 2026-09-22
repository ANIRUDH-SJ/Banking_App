package com.netbanking.security;

import com.netbanking.user.domain.AppUser;
import io.jsonwebtoken.Jwts;
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
    public JwtService(@Value("${app.security.jwt.secret}") String secret, @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
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
    }
    public String createToken(AppUser user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(role -> role.getRoleCode()).sorted().toList();
        return Jwts.builder().subject(user.getUserId().toString()).claim("username", user.getUsername()).claim("roles", roles)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(expiration))).signWith(key).compact();
    }
    public JwtPrincipal parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        @SuppressWarnings("unchecked") List<String> roles = claims.get("roles", List.class);
        return new JwtPrincipal(Long.valueOf(claims.getSubject()), claims.get("username", String.class), roles == null ? List.of() : roles);
    }
    public record JwtPrincipal(Long userId, String username, List<String> roles) { }
}
