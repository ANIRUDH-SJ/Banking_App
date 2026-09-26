package com.netbanking.otp;

import static org.assertj.core.api.Assertions.*;

import com.netbanking.ServiceTestBase;
import com.netbanking.discovery.OtpClient.Authorization;
import com.netbanking.otp.domain.*;
import com.netbanking.otp.repository.OtpVerificationRepository;
import com.netbanking.otp.service.OtpAuthorizationService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

class OtpAuthorizationIntegrationTest extends ServiceTestBase {
    @Autowired OtpAuthorizationService authorizations;
    @Autowired OtpVerificationRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS otp_authorization(caller VARCHAR(50),operation_id"
                        + " VARCHAR(64),user_id BIGINT,purpose VARCHAR(30),intent_digest"
                        + " VARCHAR(64),PRIMARY KEY(caller,operation_id))");
        jdbc.update("DELETE FROM otp_authorization");
        otps.deleteAll();
        otps.saveAndFlush(
                new OtpVerification(
                        7L,
                        "challenge",
                        encoder.encode("123456"),
                        OtpPurpose.FUND_TRANSFER,
                        Instant.now().plusSeconds(120),
                        "a".repeat(64)));
    }

    Authorization request(String operation, String code) {
        return new Authorization(operation, 7L, "challenge", code, "FUND_TRANSFER", "a".repeat(64));
    }

    @Test
    void successfulGrantCanRecoverButCannotAuthorizeAnotherPayment() {
        assertThat(authorizations.authorize("payments-service", request("payment-1", "123456")))
                .isTrue();
        assertThat(authorizations.authorize("payments-service", request("payment-1", "000000")))
                .isTrue();
        assertThat(authorizations.authorize("payments-service", request("payment-2", "123456")))
                .isFalse();
        assertThat(
                        authorizations.authorize(
                                "payments-service",
                                new Authorization(
                                        "payment-1",
                                        7L,
                                        "challenge",
                                        "123456",
                                        "FUND_TRANSFER",
                                        "b".repeat(64))))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM otp_authorization", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectedCodesPersistAttemptsAndLockTheChallenge() {
        for (int i = 0; i < 5; i++)
            assertThat(authorizations.authorize("payments-service", request("payment-1", "000000")))
                    .isFalse();
        assertThat(authorizations.authorize("payments-service", request("payment-1", "123456")))
                .isFalse();
        assertThat(otps.findByChallengeId("challenge").orElseThrow().getFailedAttempts())
                .isEqualTo(5);
    }
}
