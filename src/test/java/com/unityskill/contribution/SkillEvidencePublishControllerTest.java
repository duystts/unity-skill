package com.unityskill.contribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.contribution.dto.PublishEvidenceRequest;
import com.unityskill.contribution.dto.SkillEvidenceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillEvidencePublishController.class)
@Import(SecurityConfig.class)
class SkillEvidencePublishControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean SkillEvidenceService skillEvidenceService;

    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final UUID   EVIDENCE_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private SkillEvidenceResponse approvedPublished() {
        return new SkillEvidenceResponse(
                EVIDENCE_ID.toString(), USER_ID, "770e8400-e29b-41d4-a716-446655440000",
                "APPROVED", "Backend Development", "Summary.",
                null, "[]", "2026-04-20T00:00:00Z",
                "2026-04-20T00:00:00Z", "2026-04-20T00:00:00Z",
                true, "2026-04-20T00:00:00Z");
    }

    private SkillEvidenceResponse approvedUnpublished() {
        return new SkillEvidenceResponse(
                EVIDENCE_ID.toString(), USER_ID, "770e8400-e29b-41d4-a716-446655440000",
                "APPROVED", "Backend Development", "Summary.",
                null, "[]", "2026-04-20T00:00:00Z",
                "2026-04-20T00:00:00Z", "2026-04-20T00:00:00Z",
                false, null);
    }

    // ─── PATCH /api/v1/skill-evidences/{evidenceId} ───────────────────────

    @Test
    void publish_approvedEvidence_returns200WithIsPublishedTrue() throws Exception {
        // AC1
        mockValidJwt();
        when(skillEvidenceService.setPublished(eq(EVIDENCE_ID), any(), eq(true)))
                .thenReturn(approvedPublished());

        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublished").value(true))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
    }

    @Test
    void unpublish_returns200WithIsPublishedFalse() throws Exception {
        // AC2
        mockValidJwt();
        when(skillEvidenceService.setPublished(eq(EVIDENCE_ID), any(), eq(false)))
                .thenReturn(approvedUnpublished());

        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublished").value(false));
    }

    @Test
    void publish_pendingEvidence_returns400() throws Exception {
        // AC3
        mockValidJwt();
        when(skillEvidenceService.setPublished(any(), any(), eq(true)))
                .thenThrow(new BadRequestException("Only approved evidence can be published"));

        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void publish_notOwner_returns403() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.setPublished(any(), any(), anyBoolean()))
                .thenThrow(new UnauthorizedAccessException("Cannot modify evidence belonging to another user"));

        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void publish_notFound_returns404() throws Exception {
        mockValidJwt();
        when(skillEvidenceService.setPublished(any(), any(), anyBoolean()))
                .thenThrow(new SkillEvidenceNotFoundException(EVIDENCE_ID));

        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SKILL_EVIDENCE_NOT_FOUND"));
    }

    @Test
    void publish_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/skill-evidences/{id}", EVIDENCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublishEvidenceRequest(true))))
                .andExpect(status().isUnauthorized());
    }
}
