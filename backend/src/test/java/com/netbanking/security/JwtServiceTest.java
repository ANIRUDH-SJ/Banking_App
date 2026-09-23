package com.netbanking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netbanking.user.domain.AppUser;
import io.jsonwebtoken.JwtException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            Base64.getEncoder().encodeToString(new byte[32]), 30, 5);

    @Test
    void accessAndLoginChallengeTokensCannotBeInterchanged() {
        AppUser user = user();
        String accessToken = jwtService.createToken(user);
        String loginChallenge = jwtService.createTotpLoginChallenge(user);

        assertThat(jwtService.parse(accessToken).userId()).isEqualTo(7L);
        assertThat(jwtService.parseTotpLoginChallenge(loginChallenge)).isEqualTo(7L);
        assertThatThrownBy(() -> jwtService.parse(loginChallenge)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.parseTotpLoginChallenge(accessToken)).isInstanceOf(JwtException.class);
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
