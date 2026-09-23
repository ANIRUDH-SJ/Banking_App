package com.netbanking.admin.service;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.service.AuditLogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class AdminService { private final AuditLogService auditLogService; public AdminService(AuditLogService auditLogService){this.auditLogService=auditLogService;} @Transactional(readOnly=true) public List<AuditEvent> recentAuditEvents(){return auditLogService.recentEvents();} }
