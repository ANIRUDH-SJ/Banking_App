package com.netbanking.events;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.*;

public record EventEnvelope(
        @NotBlank @Size(max = 36) String eventId,
        @NotBlank @Size(max = 80) String source,
        @NotBlank @Pattern(regexp = "AUDIT|NOTIFICATION") String type,
        @NotNull JsonNode payload) {}
