package com.unityskill.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishNotification(UUID userId, String type, Map<String, Object> payload) {
        Map<String, Object> event = Map.of(
            "type", type,
            "payload", payload,
            "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", event);
    }

    public void publishToTopic(String topic, String type, Map<String, Object> payload) {
        Map<String, Object> event = Map.of(
            "type", type,
            "payload", payload,
            "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend(topic, event);
    }
}
