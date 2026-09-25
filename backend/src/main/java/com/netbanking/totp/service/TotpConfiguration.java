package com.netbanking.totp.service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TotpConfiguration {

    @Bean
    SecretGenerator totpSecretGenerator() {
        return new DefaultSecretGenerator();
    }

    @Bean
    TimeProvider totpTimeProvider() {
        return new SystemTimeProvider();
    }

    @Bean
    CodeVerifier totpCodeVerifier(TimeProvider timeProvider) {
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6), timeProvider);
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    @Bean
    QrGenerator totpQrGenerator() {
        return new ZxingPngQrGenerator();
    }
}
