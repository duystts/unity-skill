package com.unityskill.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.common.exception.NotificationNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.entity.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock WebSocketEventPublisher webSocketEventPublisher;
    @Mock ObjectMapper objectMapper;

    @InjectMocks NotificationService notificationService;

    @Test
    void notify_savesAndPublishes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("key", "value");

        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"key\":\"value\"}");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.notify(userId, workspaceId, "TEST_EVENT", payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getType()).isEqualTo("TEST_EVENT");
        assertThat(saved.getPayloadJson()).isEqualTo("{\"key\":\"value\"}");

        verify(webSocketEventPublisher).publishNotification(userId, "TEST_EVENT", payload);
    }

    @Test
    void markRead_ownNotification_setsReadTrue() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
            .userId(userId)
            .workspaceId(UUID.randomUUID())
            .type("TEST")
            .payloadJson("{}")
            .build();
        notification.setRead(false);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.markRead(notificationId, userId);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_otherUsersNotification_throwsUnauthorized() {
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
            .userId(ownerId)
            .workspaceId(UUID.randomUUID())
            .type("TEST")
            .payloadJson("{}")
            .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(notificationId, callerId))
            .isInstanceOf(UnauthorizedAccessException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_notFound_throwsNotificationNotFound() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(notificationId, UUID.randomUUID()))
            .isInstanceOf(NotificationNotFoundException.class);
    }
}
