package com.unityskill.portfolio;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.portfolio.dto.PublicPortfolioResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
@Import(SecurityConfig.class)
class PortfolioControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean PortfolioService portfolioService;

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private PublicPortfolioResponse portfolioWithSkills() {
        var item = new PublicPortfolioResponse.EvidenceItem(
                "880e8400-e29b-41d4-a716-446655440000",
                "Backend Development", "Developer showed Java skills.",
                "2026-04-20T00:00:00Z");
        var group = new PublicPortfolioResponse.SkillGroup(
                "Backend Development", 1, List.of(item));
        return new PublicPortfolioResponse(
                USER_ID.toString(), "Alice", List.of(group), List.of(), null);
    }

    private PublicPortfolioResponse emptyPortfolio() {
        return new PublicPortfolioResponse(
                USER_ID.toString(), "Alice", List.of(), List.of(), null);
    }

    @Test
    void getPublicPortfolio_withPublishedItems_returns200WithSkills() throws Exception {
        // AC1: returns published evidence grouped by skill_category
        when(portfolioService.getPublicPortfolio(USER_ID)).thenReturn(portfolioWithSkills());

        mockMvc.perform(get("/api/v1/public/portfolio/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.skills[0].skillCategory").value("Backend Development"))
                .andExpect(jsonPath("$.data.skills[0].count").value(1))
                .andExpect(jsonPath("$.data.endorsements").isArray())
                .andExpect(jsonPath("$.data.streak").doesNotExist());
    }

    @Test
    void getPublicPortfolio_noPublishedEvidence_returns200WithEmptySkills() throws Exception {
        // AC2: no published evidence → empty skills array
        when(portfolioService.getPublicPortfolio(USER_ID)).thenReturn(emptyPortfolio());

        mockMvc.perform(get("/api/v1/public/portfolio/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.skills").isEmpty())
                .andExpect(jsonPath("$.data.endorsements").isEmpty());
    }

    @Test
    void getPublicPortfolio_evidenceItemExcludesSensitiveFields() throws Exception {
        // AC3 (Story 9.3): public portfolio EvidenceItem must NOT contain developerNotes,
        // workspaceId, or sourceEvents — internal fields must never be publicly visible
        when(portfolioService.getPublicPortfolio(USER_ID)).thenReturn(portfolioWithSkills());

        mockMvc.perform(get("/api/v1/public/portfolio/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills[0].items[0].aiSummary").exists())
                .andExpect(jsonPath("$.data.skills[0].items[0].developerNotes").doesNotExist())
                .andExpect(jsonPath("$.data.skills[0].items[0].workspaceId").doesNotExist())
                .andExpect(jsonPath("$.data.skills[0].items[0].sourceEvents").doesNotExist());
    }

    @Test
    void getPublicPortfolio_userNotFound_returns403() throws Exception {
        // AC1: unknown user → 403 FORBIDDEN (prevents user enumeration)
        when(portfolioService.getPublicPortfolio(any()))
                .thenThrow(new UnauthorizedAccessException("User not found"));

        mockMvc.perform(get("/api/v1/public/portfolio/{userId}", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getPublicPortfolio_noAuthHeader_returns200() throws Exception {
        // AC3: public endpoint — no Authorization header required
        when(portfolioService.getPublicPortfolio(USER_ID)).thenReturn(emptyPortfolio());

        // Intentionally no Authorization header
        mockMvc.perform(get("/api/v1/public/portfolio/{userId}", USER_ID))
                .andExpect(status().isOk());
    }
}
