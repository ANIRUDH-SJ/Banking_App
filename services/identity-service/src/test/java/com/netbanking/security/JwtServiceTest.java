package com.netbanking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netbanking.user.domain.AppUser;

import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtVerifier verifier;

    @org.junit.jupiter.api.BeforeEach
    void keys() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        verifier =
                new JwtVerifier(
                        Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                        "banking-identity");
        jwtService =
                new JwtService(
                        Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                        verifier,
                        "banking-identity",
                        15,
                        5);
    }

    @Test
    void accessAndLoginChallengeTokensCannotBeInterchanged() {
        AppUser user = user();
        String accessToken = jwtService.createToken(user);
        String loginChallenge = jwtService.createTotpLoginChallenge(user);

        assertThat(verifier.parse(accessToken).userId()).isEqualTo(7L);
        assertThat(jwtService.parseTotpLoginChallenge(loginChallenge)).isEqualTo(7L);
        assertThatThrownBy(() -> verifier.parse(loginChallenge)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parseTotpLoginChallenge(accessToken))
                .isInstanceOf(JwtException.class);
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
