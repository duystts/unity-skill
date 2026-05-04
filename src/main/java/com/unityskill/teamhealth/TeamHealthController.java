package com.unityskill.teamhealth;

import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.BlockedDecisionsResponse;
import com.unityskill.teamhealth.dto.TeamHealthResponse;
import com.unityskill.teamhealth.dto.WorkloadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class TeamHealthController {

    private final TeamHealthService teamHealthService;
    private final WorkloadAnalyzer workloadAnalyzer;

    /**
     * GET /api/v1/workspaces/{workspaceId}/team-health
     * AC1: Returns team health summary (members, tickets, overdue counts).
     * AC3: Only PM/Admin — service throws UnauthorizedAccessException for DEVELOPER role.
     */
    @GetMapping("/{workspaceId}/team-health")
    public ResponseEntity<Map<String, Object>> getTeamHealth(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        TeamHealthResponse result = teamHealthService.getTeamHealth(
                workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/team-health/workload
     * Story 8.3 AC1, AC4: Returns per-member workload with PM-configurable thresholds, sorted desc.
     */
    @GetMapping("/{workspaceId}/team-health/workload")
    public ResponseEntity<Map<String, Object>> getWorkload(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        WorkloadResponse result = teamHealthService.getWorkload(
                workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/team-health/workload/{targetUserId}/tickets
     * Story 8.3 AC3: Returns a member's open tickets for the detail/filtered view.
     * Uses HashMap (not Map.of) because stageId may be null.
     */
    @GetMapping("/{workspaceId}/team-health/workload/{targetUserId}/tickets")
    public ResponseEntity<Map<String, Object>> getMemberOpenTickets(
            @PathVariable UUID workspaceId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal String userId) {
        List<Ticket> tickets = teamHealthService.getMemberOpenTickets(
                workspaceId, UUID.fromString(userId), targetUserId);
        List<Map<String, Object>> result = tickets.stream()
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",      t.getId().toString());
                    m.put("title",   t.getTitle());
                    m.put("stageId", t.getStageId() != null ? t.getStageId().toString() : null);
                    return m;
                })
                .toList();
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/team-health/blocked-decisions
     * AC1, AC2: Returns blocked tickets with reason and duration.
     * AC3: DEVELOPER → 403 (role check in WorkloadAnalyzer).
     */
    @GetMapping("/{workspaceId}/team-health/blocked-decisions")
    public ResponseEntity<Map<String, Object>> getBlockedDecisions(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        BlockedDecisionsResponse result = workloadAnalyzer.computeBlockedDecisions(
                workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }
}
