package com.netbanking.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class GatewayRoutes {
    private static final Map<String, String> ROUTES =
            Map.ofEntries(
                    Map.entry("auth", "identity-service"),
                    Map.entry("profile", "identity-service"),
                    Map.entry("accounts", "accounts-ledger-service"),
                    Map.entry("beneficiaries", "payments-service"),
                    Map.entry("billers", "payments-service"),
                    Map.entry("transfers", "payments-service"),
                    Map.entry("bill-payments", "payments-service"),
                    Map.entry("cards", "products-service"),
                    Map.entry("loans", "products-service"),
                    Map.entry("notifications", "notification-service"),
                    Map.entry("admin", "audit-reporting-service"));

    public String serviceFor(String path) {
        // Reject encoded separators, matrix parameters and dot segments before resolving a route.
        if (!path.startsWith("/api/v1/")
                || path.contains("%")
                || path.contains(";")
                || path.contains("\\")
                || path.contains("//")
                || java.util.Arrays.stream(path.split("/"))
                        .anyMatch(s -> s.equals(".") || s.equals(".."))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String service = ROUTES.get(path.substring(8).split("/", 2)[0]);
        if (service == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return service;
    }
}
