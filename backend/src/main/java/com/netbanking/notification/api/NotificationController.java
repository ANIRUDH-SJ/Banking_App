package com.netbanking.notification.api;

import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.service.NotificationService;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return toResponse(notificationService.createInApp(currentUserId(), request.notificationType(), request.title(), request.message()));
    }
    @GetMapping
    public List<NotificationResponse> getForUser() {
        return notificationService.getForUser(currentUserId()).stream().map(this::toResponse).toList();
    }
    @PatchMapping("/{notificationId}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long notificationId) { notificationService.markRead(currentUserId(), notificationId); }
    private Long currentUserId() { return SecurityContextHelper.currentUserId(); }
    private NotificationResponse toResponse(Notification notification) { return new NotificationResponse(notification.getNotificationId(), notification.getUserId(), notification.getTitle(), notification.getMessage(), notification.isRead(), notification.getCreatedAt()); }
}
