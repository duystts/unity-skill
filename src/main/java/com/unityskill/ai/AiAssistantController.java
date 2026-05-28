package com.unityskill.ai;

import com.unityskill.ai.dto.AiChatRequest;
import com.unityskill.ai.dto.AiChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService assistantService;

    /**
     * POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/ai/chat
     *
     * Body: { "message": "...", "history": [{"role": "user", "content": "..."}, ...] }
     * Returns: { "data": { "reply": "..." } }
     */
    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/ai/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal String userId) {

        String reply = assistantService.chat(
                workspaceId, projectId, UUID.fromString(userId),
                request.message(), request.history());

        return ResponseEntity.ok(Map.of("data", new AiChatResponse(reply)));
    }
}
