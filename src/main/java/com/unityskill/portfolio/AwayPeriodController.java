package com.unityskill.portfolio;

import com.unityskill.portfolio.dto.AwayPeriodRequest;
import com.unityskill.portfolio.dto.AwayPeriodResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/away-periods")
@RequiredArgsConstructor
public class AwayPeriodController {

    private final AwayPeriodService awayPeriodService;

    /**
     * AC1: Declare a new away period.
     * POST /api/v1/users/me/away-periods
     * Body: { "startDate": "2026-04-10", "endDate": "2026-04-17" }
     * Response: 201 Created, { "data": { away period object } }
     *
     * Note: @AuthenticationPrincipal is String (JwtAuthFilter sets principal as userId string).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAwayPeriod(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AwayPeriodRequest request) {
        AwayPeriodResponse result = awayPeriodService.createAwayPeriod(UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    /**
     * AC4: List all away periods for the authenticated user.
     * GET /api/v1/users/me/away-periods
     * Response: 200 OK, { "data": [ ... ] }
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAwayPeriods(
            @AuthenticationPrincipal String userId) {
        List<AwayPeriodResponse> result = awayPeriodService.getAwayPeriods(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }
}
