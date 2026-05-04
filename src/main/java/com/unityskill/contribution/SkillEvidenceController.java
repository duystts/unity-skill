package com.unityskill.contribution;

import com.unityskill.contribution.dto.ReviewEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import com.unityskill.contribution.dto.SkillProfileResponse;
import com.unityskill.contribution.entity.EvidenceStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class SkillEvidenceController {

    private final SkillEvidenceService skillEvidenceService;

    /**
     * GET /api/v1/workspaces/{workspaceId}/skill-evidences?status=PENDING
     * Returns only the calling developer's own evidence (AC1).
     */
    @GetMapping("/{workspaceId}/skill-evidences")
    public ResponseEntity<Map<String, Object>> getEvidence(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) EvidenceStatus status,
            @AuthenticationPrincipal String userId) {
        List<SkillEvidenceResponse> result =
                skillEvidenceService.getEvidence(workspaceId, UUID.fromString(userId), status);
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/skill-profile
     * Returns the authenticated developer's private skill profile.
     * AC1: APPROVED evidence grouped by category.
     * AC2: Always caller's own profile (403 if not a workspace member).
     */
    @GetMapping("/{workspaceId}/skill-profile")
    public ResponseEntity<Map<String, Object>> getSkillProfile(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        SkillProfileResponse result =
                skillEvidenceService.getSkillProfile(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    /**
     * PATCH /api/v1/workspaces/{workspaceId}/skill-evidences/{evidenceId}
     * Body: { "action": "APPROVE" | "REJECT" | "EDIT", "developerNotes": "..." }
     * AC2 (APPROVE), AC3 (REJECT), AC4 (EDIT), AC5 (ownership).
     */
    @PatchMapping("/{workspaceId}/skill-evidences/{evidenceId}")
    public ResponseEntity<Map<String, Object>> reviewEvidence(
            @PathVariable UUID workspaceId,
            @PathVariable UUID evidenceId,
            @Valid @RequestBody ReviewEvidenceRequest request,
            @AuthenticationPrincipal String userId) {
        SkillEvidenceResponse result =
                skillEvidenceService.reviewEvidence(evidenceId, workspaceId, UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("data", result));
    }
}
