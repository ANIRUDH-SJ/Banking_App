package com.netbanking.notification.repository;

import com.netbanking.notification.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    long countByUserIdAndIsRead(Long userId, String isRead);

    java.util.List<Notification> findByUserIdAndIsRead(Long userId, String isRead);
}
