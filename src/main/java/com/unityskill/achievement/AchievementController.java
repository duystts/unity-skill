package com.unityskill.achievement;

import com.unityskill.achievement.dto.AchievementResponse;
import com.unityskill.achievement.dto.TagsResponse;
import com.unityskill.achievement.dto.TicketTagRequest;
import com.unityskill.achievement.dto.UserAchievementResponse;
import com.unityskill.achievement.entity.TicketTagValue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    // ── Ticket Tags ───────────────────────────────────────────────────────────

    @GetMapping("/api/v1/workspaces/{workspaceId}/tickets/{ticketId}/tags")
    public ResponseEntity<Map<String, Object>> getTags(
        @PathVariable UUID workspaceId,
        @PathVariable UUID ticketId,
        @AuthenticationPrincipal String userId
    ) {
        TagsResponse tags = achievementService.getTagsForTicket(workspaceId, ticketId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", tags));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/tickets/{ticketId}/tags")
    public ResponseEntity<Map<String, Object>> addTag(
        @PathVariable UUID workspaceId,
        @PathVariable UUID ticketId,
        @Valid @RequestBody TicketTagRequest req,
        @AuthenticationPrincipal String userId
    ) {
        TagsResponse tags = achievementService.addTag(workspaceId, ticketId, req.tag(), UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", tags));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/tickets/{ticketId}/tags/{tag}")
    public ResponseEntity<Map<String, Object>> removeTag(
        @PathVariable UUID workspaceId,
        @PathVariable UUID ticketId,
        @PathVariable TicketTagValue tag,
        @AuthenticationPrincipal String userId
    ) {
        TagsResponse tags = achievementService.removeTag(workspaceId, ticketId, tag, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", tags));
    }

    // ── Achievements ──────────────────────────────────────────────────────────

    @GetMapping("/api/v1/users/me/achievements")
    public ResponseEntity<Map<String, Object>> getMyAchievements(
        @AuthenticationPrincipal String userId
    ) {
        List<UserAchievementResponse> achievements = achievementService.getMyAchievements(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", achievements));
    }

    @GetMapping("/api/v1/achievements")
    public ResponseEntity<Map<String, Object>> getAllDefinitions() {
        List<AchievementResponse> defs = achievementService.getAllDefinitions();
        return ResponseEntity.ok(Map.of("data", defs));
    }
}
