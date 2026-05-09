package com.unityskill.collaboration;

import com.unityskill.collaboration.dto.ChatMessageRequest;
import com.unityskill.collaboration.dto.ChatMessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/chat")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal String userId) {
        ChatMessageResponse response = chatService.sendMessage(
                workspaceId, projectId, UUID.fromString(userId), request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", response));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/chat")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal String userId) {
        Page<ChatMessageResponse> result = chatService.getMessages(
                workspaceId, projectId, UUID.fromString(userId), page, size);
        return ResponseEntity.ok(Map.of(
                "data", result.getContent(),
                "pagination", Map.of(
                        "page",  result.getNumber(),
                        "size",  result.getSize(),
                        "total", result.getTotalElements()
                )
        ));
    }
}
