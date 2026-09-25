package com.netbanking.notification.api;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        Long userId,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt) {}
