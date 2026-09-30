package com.netbanking.notification.repository;

import com.netbanking.notification.domain.NotificationDelivery;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "notification")
    @Query(
            "select delivery from NotificationDelivery delivery "
                    + "where ((delivery.deliveryStatus in "
                    + "(com.netbanking.notification.domain.DeliveryStatus.PENDING, "
                    + "com.netbanking.notification.domain.DeliveryStatus.FAILED) "
                    + "and delivery.nextAttemptAt <= :now) "
                    + "or (delivery.deliveryStatus = "
                    + "com.netbanking.notification.domain.DeliveryStatus.PROCESSING "
                    + "and delivery.attemptedAt <= :staleBefore)) "
                    + "order by delivery.nextAttemptAt, delivery.notificationDeliveryId")
    List<NotificationDelivery> findReadyForUpdate(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);

    @EntityGraph(attributePaths = "notification")
    @Query(
            "select delivery from NotificationDelivery delivery "
                    + "where delivery.notificationDeliveryId = :deliveryId")
    java.util.Optional<NotificationDelivery> findWithNotificationById(
            @Param("deliveryId") Long deliveryId);
}
