package com.netbanking.notification.service;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.notification.domain.Notification;
import com.netbanking.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class NotificationService {
 private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));
 private final NotificationRepository repository; private final AuditLogService auditLogService;
 public NotificationService(NotificationRepository repository,AuditLogService auditLogService){this.repository=repository;this.auditLogService=auditLogService;}
 @Transactional public Notification createInApp(Long userId,String type,String title,String message){
  requireText(type,"Notification type",50); requireText(title,"Notification title",200); requireText(message,"Notification message",1000);
  Notification saved=repository.save(new Notification(userId,type.strip(),title.strip(),message.strip()));auditLogService.record(userId,"NOTIFICATION_CREATED","NOTIFICATION",String.valueOf(saved.getNotificationId()),"SUCCESS");return saved;
 }
 @Transactional(readOnly=true) public Page<Notification> getForUser(Long userId,int page,int size){
  if(page<0||size<1||size>100) throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
  return repository.findByUserId(userId,PageRequest.of(page,size,NEWEST_FIRST));
 }
 @Transactional public void markRead(Long userId,Long notificationId){Notification n=repository.findById(notificationId).orElseThrow(()->new IllegalArgumentException("Notification was not found."));if(!n.getUserId().equals(userId))throw new SecurityException("Notification does not belong to this user.");n.markRead();}
 private static void requireText(String value,String field,int maximum){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required.");if(value.strip().length()>maximum)throw new IllegalArgumentException(field+" must not exceed "+maximum+" characters.");}
}
