package com.netbanking.admin.api;

import com.netbanking.admin.service.AdminService;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.common.api.PagedResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminAuditController {
    private final AdminService adminService;

    public AdminAuditController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/audit-events")
    public PagedResponse<AuditEventResponse> auditEvents(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(
                adminService
                        .search(
                                new AuditSearchFilter(userId, eventType, outcome, from, to),
                                page,
                                size)
                        .map(this::toResponse));
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getAuditEventId(),
                event.getUserId(),
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getOutcome(),
                event.getEventDetails(),
                event.getOccurredAt());
    }
}
