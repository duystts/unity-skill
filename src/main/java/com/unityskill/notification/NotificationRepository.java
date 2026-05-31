package com.unityskill.notification;

import com.unityskill.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    /** Used by mark-all-read — fetch all unread for a user in one query. */
    java.util.List<Notification> findAllByUserIdAndIsReadFalse(UUID userId);
}
