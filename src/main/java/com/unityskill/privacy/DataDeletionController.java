package com.unityskill.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DataDeletionController {

    private final DataDeletionService dataDeletionService;

    /**
     * DELETE /api/v1/users/me/contribution-data
     * AC1: permanently deletes all contribution data for the authenticated user.
     * AC3: @AuthenticationPrincipal scopes deletion to the requesting user only.
     * AC4: returns 200 with deletion counts per table.
     * AC5: idempotent — repeated calls return zeros, not errors.
     */
    @DeleteMapping("/api/v1/users/me/contribution-data")
    public ResponseEntity<Map<String, Object>> deleteContributionData(
            @AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);
        Map<String, Integer> counts = dataDeletionService.deleteAllContributionData(uid);
        return ResponseEntity.ok(Map.of("data", Map.of("deletedRecords", counts)));
    }
}
