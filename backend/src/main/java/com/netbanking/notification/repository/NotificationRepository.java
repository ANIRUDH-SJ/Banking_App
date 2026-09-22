package com.netbanking.notification.repository;
import com.netbanking.notification.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification,Long>{ List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId); }
