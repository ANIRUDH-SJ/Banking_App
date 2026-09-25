package com.netbanking;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Base64;

@SpringBootTest
@ActiveProfiles("local")
public abstract class ServiceTestBase {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        p.add(
                "app.security.jwt.public-key",
                () -> Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        p.add(
                "app.security.jwt.private-key",
                () -> Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        p.add(
                "app.security.totp.encryption-key",
                () -> Base64.getEncoder().encodeToString(new byte[32]));
        p.add("app.internal.token", () -> "test-only-token-not-for-deployment");
        for (String service :
                new String[] {
                    "identity-service",
                    "accounts-ledger-service",
                    "payments-service",
                    "products-service",
                    "notification-service",
                    "audit-reporting-service"
                }) p.add("app.internal.token-hashes." + service, () -> "a".repeat(64));
        p.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:identity-service;MODE=Oracle;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        p.add("spring.datasource.username", () -> "sa");
        p.add("spring.datasource.password", () -> "");
        p.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        p.add("spring.flyway.enabled", () -> false);
        p.add("eureka.client.enabled", () -> false);
        p.add("app.scheduling.enabled", () -> false);
    }
}
