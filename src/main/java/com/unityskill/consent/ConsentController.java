package com.unityskill.consent;

import com.unityskill.consent.entity.ConsentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    /**
     * POST /api/v1/users/me/consent
     * AC3: Authenticated user records consent. Idempotent — safe to call multiple times.
     * This path MUST be in ConsentCheckFilter's exempt list so un-consented users can reach it.
     */
    @PostMapping("/api/v1/users/me/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
            @AuthenticationPrincipal String userId) {
        ConsentRecord record = consentService.recordConsent(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", Map.of(
                "consentedAt",    record.getConsentedAt().toString(),
                "consentVersion", record.getConsentVersion()
        )));
    }
}
