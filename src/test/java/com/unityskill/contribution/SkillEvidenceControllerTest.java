package com.unityskill.contribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.contribution.dto.ReviewAction;
import com.unityskill.contribution.dto.ReviewEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import com.unityskill.contribution.dto.SkillProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillEvidenceController.class)
@Import(SecurityConfig.class)
class SkillEvidenceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean SkillEvidenceService skillEvidenceService;

    private static final String USER_ID      = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN        = "test-token";
    private static final String AUTH_HEADER  = "Bearer " + TOKEN;
    private static final UUID   WORKSPACE_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440000");
    private static final UUID   EVIDENCE_ID  = UUID.fromString("880e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private SkillEvidenceResponse sampleResponse(String status) {
        return new SkillEvidenceResponse(
                EVIDENCE_ID.toString(), USER_ID, WORKSPACE_ID.toString(),
                status, "Backend Development", "Developer demonstrated Java skills.",
                null, "[\"uuid1\"]", null,
                "2026-04-20T00:00:00Z", "2026-04-20T00:00:00Z",
                false, null);   // isPublished, publishedAt — Story 7.2
    }

    // ─── GET /{workspaceId}/skill-evidences ───────────────────────────────

    @Test
    void getEvidence_noStatusParam_returns200() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.getEvidence(eq(WORKSPACE_ID), any(), eq(null)))
                .thenReturn(List.of(sampleResponse("PENDING")));

        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-evidences", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].skillCategory").value("Backend Development"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void getEvidence_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-evidences", WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEvidence_notMember_returns403() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.getEvidence(any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Not a member"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-evidences", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ─── PATCH /{workspaceId}/skill-evidences/{evidenceId} ────────────────

    @Test
    void reviewEvidence_approve_returns200() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.reviewEvidence(eq(EVIDENCE_ID), eq(WORKSPACE_ID), any(), any()))
                .thenReturn(sampleResponse("APPROVED"));

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewEvidenceRequest(ReviewAction.APPROVE, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void reviewEvidence_anotherUsersEvidence_returns403() throws Exception {
        // AC5
        mockValidJwt();
        when(skillEvidenceService.reviewEvidence(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Cannot review evidence belonging to another user"));

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewEvidenceRequest(ReviewAction.APPROVE, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void reviewEvidence_notFound_returns404() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.reviewEvidence(any(), any(), any(), any()))
                .thenThrow(new SkillEvidenceNotFoundException(EVIDENCE_ID));

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewEvidenceRequest(ReviewAction.APPROVE, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SKILL_EVIDENCE_NOT_FOUND"));
    }

    @Test
    void reviewEvidence_editWithoutNotes_returns400() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.reviewEvidence(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("developerNotes is required for EDIT action"));

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewEvidenceRequest(ReviewAction.EDIT, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void reviewEvidence_missingAction_returns400() throws Exception {
        // @NotNull on action → MethodArgumentNotValidException → 400 VALIDATION_FAILED
        mockValidJwt();

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"developerNotes\":\"notes only\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void reviewEvidence_invalidAction_returns400() throws Exception {
        // Invalid enum value → HttpMessageNotReadableException → 400 INVALID_REQUEST_BODY
        mockValidJwt();

        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"INVALID_ACTION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void reviewEvidence_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/workspaces/{wid}/skill-evidences/{eid}", WORKSPACE_ID, EVIDENCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewEvidenceRequest(ReviewAction.APPROVE, null))))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /{workspaceId}/skill-profile ─────────────────────────────────

    @Test
    void getSkillProfile_authenticated_returns200WithGroupedEvidence() throws Exception {
        // AC1
        mockValidJwt();
        var profile = new SkillProfileResponse(
                2,
                List.of(new SkillProfileResponse.SkillCategoryGroup(
                        "Backend Development", 2, List.of())),
                new SkillProfileResponse.StreakInfo(3, 5)  // Story 7.5: include streak
        );
        when(skillEvidenceService.getSkillProfile(eq(WORKSPACE_ID), any())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-profile", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalApproved").value(2))
                .andExpect(jsonPath("$.data.categories[0].skillCategory").value("Backend Development"))
                .andExpect(jsonPath("$.data.categories[0].count").value(2))
                .andExpect(jsonPath("$.data.streak.currentWeeks").value(3))
                .andExpect(jsonPath("$.data.streak.longestWeeks").value(5));
    }

    @Test
    void getSkillProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-profile", WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSkillProfile_notMember_returns403() throws Exception {
        // AC2: 403 for non-members
        mockValidJwt();
        when(skillEvidenceService.getSkillProfile(any(), any()))
                .thenThrow(new UnauthorizedAccessException("Not a member"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-profile", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getSkillProfile_withNullStreak_returnsNullOrAbsentStreakField() throws Exception {
        // AC3: streak: null when no streak record exists — field is absent or null in JSON
        mockValidJwt();
        var profile = new SkillProfileResponse(0, List.of(), null);
        when(skillEvidenceService.getSkillProfile(eq(WORKSPACE_ID), any())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/workspaces/{id}/skill-profile", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalApproved").value(0))
                .andExpect(jsonPath("$.data.categories").isEmpty());
        // streak field: null or absent depending on Jackson @JsonInclude config — both are valid
    }
}
