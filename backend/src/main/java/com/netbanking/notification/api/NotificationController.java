package com.netbanking.notification.api;

import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.service.NotificationService;
import com.netbanking.security.SecurityContextHelper;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }
    @GetMapping
    public Page<NotificationResponse> getForUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getForUser(currentUserId(), page, size).map(this::toResponse);
    }
    @PatchMapping("/{notificationId}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long notificationId) { notificationService.markRead(currentUserId(), notificationId); }
    private Long currentUserId() { return SecurityContextHelper.currentUserId(); }
    private NotificationResponse toResponse(Notification notification) { return new NotificationResponse(notification.getNotificationId(), notification.getUserId(), notification.getTitle(), notification.getMessage(), notification.isRead(), notification.getCreatedAt()); }
}
