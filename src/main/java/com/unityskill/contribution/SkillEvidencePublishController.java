package com.unityskill.contribution;

import com.unityskill.contribution.dto.PublishEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skill-evidences")
@RequiredArgsConstructor
public class SkillEvidencePublishController {

    private final SkillEvidenceService skillEvidenceService;

    /**
     * PATCH /api/v1/skill-evidences/{evidenceId}
     * Body: { "isPublished": true | false }
     * AC1 (publish approved evidence), AC2 (unpublish), AC3 (400 for non-APPROVED).
     */
    @PatchMapping("/{evidenceId}")
    public ResponseEntity<Map<String, Object>> setPublished(
            @PathVariable UUID evidenceId,
            @RequestBody PublishEvidenceRequest request,
            @AuthenticationPrincipal String userId) {
        SkillEvidenceResponse result =
                skillEvidenceService.setPublished(evidenceId, UUID.fromString(userId), request.isPublished());
        return ResponseEntity.ok(Map.of("data", result));
    }
}
