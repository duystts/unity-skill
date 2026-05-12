package com.unityskill.auth;

import com.unityskill.auth.dto.PreferencesResponse;
import com.unityskill.auth.dto.UpdatePreferencesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** GET /api/v1/users/me — trả về thông tin user hiện tại */
    @GetMapping("/api/v1/users/me")
    public ResponseEntity<Map<String, Object>> getMe(
            @AuthenticationPrincipal String userId) {
        var user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of("data", Map.of(
            "id", user.getId().toString(),
            "email", user.getEmail(),
            "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
            "uiMode", user.getUiMode() != null ? user.getUiMode().name() : "SERIOUS",
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "isIncognito", user.isIncognito()
        )));
    }

    /** PATCH /api/v1/users/me/display-name — cập nhật tên hiển thị */
    @PatchMapping("/api/v1/users/me/display-name")
    public ResponseEntity<Map<String, Object>> updateDisplayName(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {
        String displayName = body.get("displayName");
        if (displayName == null || displayName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "displayName is required"));
        }
        var user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setDisplayName(displayName.trim());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("data", Map.of(
            "id", user.getId().toString(),
            "displayName", user.getDisplayName()
        )));
    }

    @PatchMapping("/api/v1/users/me/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal String userId) {
        var user = authService.updatePreferences(UUID.fromString(userId), request.uiMode());
        return ResponseEntity.ok(Map.of("data", Map.of(
            "id", user.id(),
            "email", user.email(),
            "displayName", user.displayName(),
            "uiMode", user.uiMode()
        )));
    }

    @GetMapping("/api/v1/users/me/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(
            @AuthenticationPrincipal String userId) {
        PreferencesResponse result = authService.getPreferences(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    @PostMapping("/api/v1/users/me/incognito/enable")
    public ResponseEntity<Map<String, Object>> enableIncognito(
            @AuthenticationPrincipal String userId) {
        PreferencesResponse result = authService.enableIncognito(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    @PostMapping("/api/v1/users/me/incognito/disable")
    public ResponseEntity<Map<String, Object>> disableIncognito(
            @AuthenticationPrincipal String userId) {
        PreferencesResponse result = authService.disableIncognito(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * GET /api/v1/users/me/portfolio-url — Story 7.3 AC4.
     * Returns the shareable public portfolio URL for the authenticated developer.
     */
    @GetMapping("/api/v1/users/me/portfolio-url")
    public ResponseEntity<Map<String, Object>> getPortfolioUrl(
            @AuthenticationPrincipal String userId) {
        String portfolioUrl = frontendUrl + "/portfolio/" + userId;
        return ResponseEntity.ok(Map.of("data", Map.of("portfolioUrl", portfolioUrl)));
    }
}
