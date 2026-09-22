package com.netbanking.audit.repository;
import com.netbanking.audit.domain.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long>{ List<AuditEvent> findTop100ByOrderByOccurredAtDesc(); }
