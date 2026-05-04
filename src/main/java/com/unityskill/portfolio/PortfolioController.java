package com.unityskill.portfolio;

import com.unityskill.portfolio.dto.PublicPortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    /**
     * GET /api/v1/public/portfolio/{userId} — No authentication required.
     * SecurityConfig already permits all /api/v1/public/** — no changes needed there.
     * AC1: Returns only published evidence grouped by skill_category.
     * AC2: Returns empty skills/endorsements/streak when no published evidence.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getPublicPortfolio(
            @PathVariable UUID userId) {
        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);
        return ResponseEntity.ok(Map.of("data", result));
    }
}
