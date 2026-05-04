package com.unityskill.portfolio;

import com.unityskill.portfolio.dto.EndorsementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skill-evidences")
@RequiredArgsConstructor
public class EndorsementController {

    private final EndorsementService endorsementService;

    /**
     * POST /api/v1/skill-evidences/{evidenceId}/endorsements
     * AC1: Creates an endorsement for published evidence by a workspace member.
     * No request body — endorser identity comes from JWT principal.
     * Returns 201 Created on success.
     */
    @PostMapping("/{evidenceId}/endorsements")
    public ResponseEntity<Map<String, Object>> createEndorsement(
            @PathVariable UUID evidenceId,
            @AuthenticationPrincipal String endorserId) {
        EndorsementResponse result =
                endorsementService.createEndorsement(evidenceId, UUID.fromString(endorserId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }
}
