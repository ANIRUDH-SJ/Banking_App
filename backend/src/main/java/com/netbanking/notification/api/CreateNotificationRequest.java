package com.netbanking.notification.api;

import jakarta.validation.constraints.NotBlank;

public record CreateNotificationRequest(
        @NotBlank String notificationType,
        @NotBlank String title,
        @NotBlank String message) { }
