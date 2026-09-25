package com.netbanking.audit.service;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class AuditLogService {
 private final AuditEventRepository repository; public AuditLogService(AuditEventRepository repository){this.repository=repository;}
 @Transactional public void record(Long userId,String type,String entityType,String entityId,String outcome){repository.save(new AuditEvent(userId,type,entityType,entityId,outcome));}
 @Transactional public void record(Long userId,String type,String entityType,String entityId,String outcome,String details){repository.save(new AuditEvent(userId,type,entityType,entityId,outcome,details));}
 @Transactional(readOnly=true) public List<AuditEvent> recentEvents(){return repository.findTop100ByOrderByOccurredAtDesc();}
}
