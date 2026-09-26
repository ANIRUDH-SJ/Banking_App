package com.netbanking.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GatewayRoutesTest {
    @Test
    void routesOnlyExplicitPublicResources() {
        var routes = new GatewayRoutes();
        assertThat(routes.serviceFor("/api/v1/accounts/1/statement.csv"))
                .isEqualTo("accounts-ledger-service");
        assertThat(routes.serviceFor("/api/v1/transfers")).isEqualTo("payments-service");
        for (String path :
                new String[] {
                    "/internal/ledger/operations",
                    "/actuator/env",
                    "/api/v1/accounts/../../../internal/events",
                    "/api/v1/accounts%2f..%2f",
                    "/api/v1/accounts;internal",
                    "//evil.example/api/v1/accounts",
                    "/api/v1/unknown"
                })
            assertThatThrownBy(() -> routes.serviceFor(path))
                    .as(path)
                    .isInstanceOf(ResponseStatusException.class);
    }
}
