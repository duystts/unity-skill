package com.unityskill.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.common.exception.NotificationNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Handles persistence and delivery of in-app notifications.
 * <p>
 * Notifications are stored in PostgreSQL and pushed to connected clients
 * via WebSocket. Read-only queries use {@code readOnly = true} transactions
 * to avoid unnecessary write locks on the notifications table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void notify(UUID userId, UUID workspaceId, String type, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        Notification notification = Notification.builder()
            .userId(userId)
            .workspaceId(workspaceId)
            .type(type)
            .payloadJson(payloadJson)
            .build();

        notificationRepository.save(notification);
        webSocketEventPublisher.publishNotification(userId, type, payload);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
            .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        var unread = notificationRepository.findAllByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(NotificationNotFoundException::new);

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("You are not allowed to modify this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
