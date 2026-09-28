package com.netbanking.admin.api;

import java.time.LocalDate;
import java.util.Locale;

public record AuditSearchFilter(
        Long userId, String eventType, AuditOutcome outcome, LocalDate from, LocalDate to) {
    public AuditSearchFilter {
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("User identifier must be positive.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Audit search start date must be on or before the end date.");
        }
        eventType =
                eventType == null || eventType.isBlank()
                        ? null
                        : eventType.strip().toUpperCase(Locale.ROOT);
    }
}
