package com.unityskill.portfolio;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.AlreadyEndorsedException;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.SkillEvidenceNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.portfolio.dto.EndorsementResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EndorsementController.class)
@Import(SecurityConfig.class)
class EndorsementControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean EndorsementService endorsementService;

    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final UUID   EVIDENCE_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private EndorsementResponse sampleEndorsement() {
        return new EndorsementResponse(
                UUID.randomUUID().toString(),
                EVIDENCE_ID.toString(),
                USER_ID,
                "Alice",
                "770e8400-e29b-41d4-a716-446655440000",
                "2026-04-20T00:00:00Z");
    }

    @Test
    void createEndorsement_validRequest_returns201() throws Exception {
        // AC1: workspace member endorses published evidence → 201 Created
        mockValidJwt();
        when(endorsementService.createEndorsement(any(), any())).thenReturn(sampleEndorsement());

        mockMvc.perform(post("/api/v1/skill-evidences/{id}/endorsements", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endorserName").value("Alice"))
                .andExpect(jsonPath("$.data.evidenceId").value(EVIDENCE_ID.toString()));
    }

    @Test
    void createEndorsement_alreadyEndorsed_returns409() throws Exception {
        // AC2: duplicate endorsement → 409 ALREADY_ENDORSED
        mockValidJwt();
        when(endorsementService.createEndorsement(any(), any()))
                .thenThrow(new AlreadyEndorsedException());

        mockMvc.perform(post("/api/v1/skill-evidences/{id}/endorsements", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_ENDORSED"))
                .andExpect(jsonPath("$.message").value("Already endorsed"));
    }

    @Test
    void createEndorsement_selfEndorsement_returns400() throws Exception {
        // AC3: owner endorses own evidence → 400 BAD_REQUEST
        mockValidJwt();
        when(endorsementService.createEndorsement(any(), any()))
                .thenThrow(new BadRequestException("Cannot endorse your own contribution"));

        mockMvc.perform(post("/api/v1/skill-evidences/{id}/endorsements", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void createEndorsement_evidenceNotFound_returns404() throws Exception {
        mockValidJwt();
        when(endorsementService.createEndorsement(any(), any()))
                .thenThrow(new SkillEvidenceNotFoundException(EVIDENCE_ID));

        mockMvc.perform(post("/api/v1/skill-evidences/{id}/endorsements", EVIDENCE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SKILL_EVIDENCE_NOT_FOUND"));
    }

    @Test
    void createEndorsement_unauthenticated_returns401() throws Exception {
        // No Authorization header → 401
        mockMvc.perform(post("/api/v1/skill-evidences/{id}/endorsements", EVIDENCE_ID))
                .andExpect(status().isUnauthorized());
    }
}
