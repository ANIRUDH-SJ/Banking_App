package com.netbanking.admin.api;

import com.netbanking.admin.service.AdminService;
import com.netbanking.audit.domain.AuditEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminAuditController {
    private final AdminService adminService;
    public AdminAuditController(AdminService adminService) { this.adminService = adminService; }
    @GetMapping("/audit-events")
    public List<AuditEventResponse> recentAuditEvents() { return adminService.recentAuditEvents().stream().map(this::toResponse).toList(); }
    private AuditEventResponse toResponse(AuditEvent event) { return new AuditEventResponse(event.getAuditEventId(), event.getUserId(), event.getEventType(), event.getOutcome(), event.getOccurredAt()); }
    public record AuditEventResponse(Long auditEventId, Long userId, String eventType, String outcome, LocalDateTime occurredAt) { }
}
