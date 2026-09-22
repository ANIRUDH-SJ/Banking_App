package com.netbanking.notification.service;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class NotificationService {
 private final NotificationRepository repository; private final AuditLogService auditLogService;
 public NotificationService(NotificationRepository repository,AuditLogService auditLogService){this.repository=repository;this.auditLogService=auditLogService;}
 @Transactional public Notification createInApp(Long userId,String type,String title,String message){Notification saved=repository.save(new Notification(userId,type,title,message));auditLogService.record(userId,"NOTIFICATION_CREATED","NOTIFICATION",String.valueOf(saved.getNotificationId()),"SUCCESS");return saved;}
 @Transactional(readOnly=true) public List<Notification> getForUser(Long userId){return repository.findByUserIdOrderByCreatedAtDesc(userId);}
 @Transactional public void markRead(Long userId,Long notificationId){Notification n=repository.findById(notificationId).orElseThrow(()->new IllegalArgumentException("Notification was not found."));if(!n.getUserId().equals(userId))throw new SecurityException("Notification does not belong to this user.");n.markRead();}
}
