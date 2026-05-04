package com.unityskill.collaboration;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.collaboration.dto.ChatMessageResponse;
import com.unityskill.collaboration.entity.ChatMessage;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.workspace.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRepository chatRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock WebSocketEventPublisher wsPublisher;
    @InjectMocks ChatService chatService;

    UUID workspaceId;
    UUID projectId;
    UUID senderId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId   = UUID.randomUUID();
        senderId    = UUID.randomUUID();
    }

    private ChatMessage savedMessage() {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .senderId(senderId)
                .content("Hello team")
                .createdAt(Instant.now())
                .build();
    }

    private User user(UUID id, String name) {
        return User.builder().id(id).email("u@test.com").displayName(name).build();
    }

    // ── sendMessage ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_member_persistsAndPublishesAndReturnsResponse() {
        ChatMessage saved = savedMessage();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(true);
        when(chatRepository.save(any())).thenReturn(saved);
        when(userRepository.findById(senderId)).thenReturn(Optional.of(user(senderId, "Alice")));

        ChatMessageResponse response = chatService.sendMessage(workspaceId, projectId, senderId, "Hello team");

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getSenderName()).isEqualTo("Alice");
        assertThat(response.getContent()).isEqualTo("Hello team");

        verify(chatRepository).save(any(ChatMessage.class));
        verify(wsPublisher).publishToTopic(
                eq("/topic/workspace/" + workspaceId + "/chat"),
                eq("NEW_MESSAGE"),
                any(Map.class));
    }

    @Test
    void sendMessage_nonMember_throwsUnauthorized() {
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(false);
        assertThatThrownBy(() -> chatService.sendMessage(workspaceId, projectId, senderId, "Hi"))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(chatRepository, wsPublisher);
    }

    @Test
    void sendMessage_webSocketPayloadContainsExpectedFields() {
        ChatMessage saved = savedMessage();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(true);
        when(chatRepository.save(any())).thenReturn(saved);
        when(userRepository.findById(senderId)).thenReturn(Optional.of(user(senderId, "Bob")));

        chatService.sendMessage(workspaceId, projectId, senderId, "Hello team");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wsPublisher).publishToTopic(any(), eq("NEW_MESSAGE"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsKeys("id", "content", "senderName", "createdAt");
        assertThat(payload.get("senderName")).isEqualTo("Bob");
        assertThat(payload.get("content")).isEqualTo("Hello team");
    }

    @Test
    void sendMessage_senderNotFoundInUserRepo_usesUnknown() {
        ChatMessage saved = savedMessage();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(true);
        when(chatRepository.save(any())).thenReturn(saved);
        when(userRepository.findById(senderId)).thenReturn(Optional.empty());

        ChatMessageResponse response = chatService.sendMessage(workspaceId, projectId, senderId, "Hi");
        assertThat(response.getSenderName()).isEqualTo("Unknown");
    }

    // ── getMessages ──────────────────────────────────────────────────────────

    @Test
    void getMessages_member_returnsMappedPage() {
        ChatMessage msg = savedMessage();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(true);
        when(chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                eq(workspaceId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(msg)));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(user(senderId, "Alice")));

        Page<ChatMessageResponse> result = chatService.getMessages(workspaceId, projectId, senderId, 0, 50);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSenderName()).isEqualTo("Alice");
    }

    @Test
    void getMessages_nonMember_throwsUnauthorized() {
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(false);
        assertThatThrownBy(() -> chatService.getMessages(workspaceId, projectId, senderId, 0, 50))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getMessages_nullSenderId_returnsDeletedUser() {
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).projectId(projectId)
                .senderId(null).content("Hi").createdAt(Instant.now()).build();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, senderId)).thenReturn(true);
        when(chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(msg)));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        Page<ChatMessageResponse> result = chatService.getMessages(workspaceId, projectId, senderId, 0, 50);
        assertThat(result.getContent().get(0).getSenderName()).isEqualTo("Deleted User");
    }
}
