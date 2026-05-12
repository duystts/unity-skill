package com.unityskill.collaboration;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.collaboration.dto.ChatMessageResponse;
import com.unityskill.collaboration.entity.ChatMessage;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WebSocketEventPublisher wsPublisher;

    @Transactional
    public ChatMessageResponse sendMessage(UUID workspaceId, UUID projectId,
                                           UUID senderId, String content) {
        requireMember(workspaceId, senderId);

        ChatMessage message = ChatMessage.builder()
                .workspaceId(workspaceId)
                .projectId(projectId)
                .senderId(senderId)
                .content(content)
                .build();
        message = chatRepository.saveAndFlush(message);

        // Resolve display name for WebSocket payload (AC2)
        String senderName = userRepository.findById(senderId)
                .map(User::getDisplayName)
                .orElse("Unknown");

        // AC2: publish to workspace-scoped topic
        wsPublisher.publishToTopic(
                "/topic/workspace/" + workspaceId + "/chat",
                "NEW_MESSAGE",
                Map.of(
                        "id",         message.getId().toString(),
                        "projectId",  projectId.toString(),
                        "content",    message.getContent(),
                        "senderName", senderName,
                        "createdAt",  message.getCreatedAt().toString()
                )
        );

        return ChatMessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderName(senderName)
                .createdAt(message.getCreatedAt())
                .build();
    }

    public Page<ChatMessageResponse> getMessages(UUID workspaceId, UUID projectId,
                                                 UUID requesterId, int page, int size) {
        requireMember(workspaceId, requesterId);

        Page<ChatMessage> messages = chatRepository
                .findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                        workspaceId, projectId, PageRequest.of(page, size));

        // Batch-load unique senders to avoid N+1 (AC3)
        var senderIds = messages.getContent().stream()
                .filter(m -> m.getSenderId() != null)
                .map(ChatMessage::getSenderId)
                .distinct()
                .toList();
        var senderNames = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        return messages.map(m -> ChatMessageResponse.builder()
                .id(m.getId())
                .content(m.getContent())
                .senderName(m.getSenderId() != null
                        ? senderNames.getOrDefault(m.getSenderId(), "Deleted User")
                        : "Deleted User")
                .createdAt(m.getCreatedAt())
                .build());
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }
    }
}
