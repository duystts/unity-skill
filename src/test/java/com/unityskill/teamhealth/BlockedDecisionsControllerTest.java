package com.unityskill.teamhealth;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.teamhealth.dto.BlockedDecisionsResponse;
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

@WebMvcTest(TeamHealthController.class)
@Import(SecurityConfig.class)
class BlockedDecisionsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TeamHealthService teamHealthService;
    @MockitoBean WorkloadAnalyzer workloadAnalyzer;

    private static final String TOKEN        = "test-token";
    private static final String AUTH_HEADER  = "Bearer " + TOKEN;
    private static final String USER_ID      = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID   WORKSPACE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void getBlockedDecisions_pmAuthenticated_returns200WithData() throws Exception {
        // AC1, AC2
        mockValidJwt();
        BlockedDecisionsResponse.BlockedTicketInfo info =
                new BlockedDecisionsResponse.BlockedTicketInfo(
                        UUID.randomUUID().toString(),
                        "Fix login bug",
                        UUID.randomUUID().toString(),
                        BlockedReason.PENDING_PR_REVIEW,
                        52L
                );
        when(workloadAnalyzer.computeBlockedDecisions(any(), any()))
                .thenReturn(new BlockedDecisionsResponse(List.of(info)));

        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/blocked-decisions", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tickets[0].title").value("Fix login bug"))
                .andExpect(jsonPath("$.data.tickets[0].blockedReason").value("PENDING_PR_REVIEW"))
                .andExpect(jsonPath("$.data.tickets[0].blockedDurationHours").value(52));
    }

    @Test
    void getBlockedDecisions_developerRole_returns403() throws Exception {
        mockValidJwt();
        when(workloadAnalyzer.computeBlockedDecisions(any(), any()))
                .thenThrow(new UnauthorizedAccessException("Only PM or Admin can view blocked decisions"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/blocked-decisions", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getBlockedDecisions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/blocked-decisions", WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }
}
