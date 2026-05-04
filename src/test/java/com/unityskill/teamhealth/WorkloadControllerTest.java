package com.unityskill.teamhealth;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.teamhealth.dto.WorkloadResponse;
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
class WorkloadControllerTest {

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
    void getWorkload_pmAuthenticated_returns200WithData() throws Exception {
        // AC1
        mockValidJwt();
        WorkloadResponse.MemberWorkloadInfo info = new WorkloadResponse.MemberWorkloadInfo(
                USER_ID, "Alice", "PM", 3, 2, WorkloadStatus.BALANCED);
        when(teamHealthService.getWorkload(any(), any()))
                .thenReturn(new WorkloadResponse(List.of(info)));

        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/workload", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].openTicketCount").value(3))
                .andExpect(jsonPath("$.data.members[0].inProgressTicketCount").value(2))
                .andExpect(jsonPath("$.data.members[0].workloadStatus").value("BALANCED"));
    }

    @Test
    void getWorkload_developerRole_returns403() throws Exception {
        // AC3 (role check)
        mockValidJwt();
        when(teamHealthService.getWorkload(any(), any()))
                .thenThrow(new UnauthorizedAccessException("Only PM or Admin can view workload"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/workload", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getWorkload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/team-health/workload", WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }
}
