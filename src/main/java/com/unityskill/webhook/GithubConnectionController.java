package com.unityskill.webhook;

import com.unityskill.webhook.dto.GithubConnectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/github")
@RequiredArgsConstructor
public class GithubConnectionController {

    private final GithubConnectionService githubConnectionService;

    // AC 3: get connection status (no token exposed)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConnection(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        GithubConnectionResponse resp = githubConnectionService.getConnection(
                workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", resp));
    }

    // AC 4: register webhook on connected GitHub repo
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> registerWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        githubConnectionService.registerWebhook(
                workspaceId, projectId, UUID.fromString(userId), body.get("repoFullName"));
        return ResponseEntity.ok(Map.of("message", "Webhook registered successfully"));
    }
}
