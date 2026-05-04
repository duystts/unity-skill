package com.unityskill.webhook;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

@RestController
@RequiredArgsConstructor
public class GithubOAuthController {

    private final GithubConnectionService githubConnectionService;

    // AC 1: authenticated PM/Admin gets GitHub OAuth authorization URL
    @GetMapping("/api/v1/auth/github")
    public ResponseEntity<Map<String, Object>> initiateOAuth(
            @RequestParam UUID projectId,
            @RequestParam UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        String authUrl = githubConnectionService.buildAuthorizationUrl(
                projectId, workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", Map.of("authUrl", authUrl)));
    }

    // AC 2: GitHub redirects here — public endpoint (no JWT required)
    @GetMapping("/api/v1/auth/github/callback")
    public void handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        String redirectUrl = githubConnectionService.handleCallback(code, state);
        response.sendRedirect(redirectUrl);
    }
}
