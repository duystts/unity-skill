package com.unityskill.workspace;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.workspace.dto.WorkspaceSettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkspaceController.class)
@Import(SecurityConfig.class)
class WorkspaceSettingsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean WorkspaceService workspaceService;

    private static final String TOKEN        = "test-token";
    private static final String AUTH_HEADER  = "Bearer " + TOKEN;
    private static final String USER_ID      = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID   WORKSPACE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void updateSettings_pmAuthenticated_returns200WithUpdatedThresholds() throws Exception {
        // AC2
        mockValidJwt();
        when(workspaceService.updateSettings(any(), any(), any()))
                .thenReturn(new WorkspaceSettingsResponse(7, 3));

        mockMvc.perform(patch("/api/v1/workspaces/{id}/settings", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overloadedThreshold\":7,\"balancedMinThreshold\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overloadedThreshold").value(7))
                .andExpect(jsonPath("$.data.balancedMinThreshold").value(3));
    }

    @Test
    void updateSettings_developerRole_returns403() throws Exception {
        // AC2: DEVELOPER cannot update settings
        mockValidJwt();
        when(workspaceService.updateSettings(any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Only PM or Admin can update workspace settings"));

        mockMvc.perform(patch("/api/v1/workspaces/{id}/settings", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overloadedThreshold\":7,\"balancedMinThreshold\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void updateSettings_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/workspaces/{id}/settings", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overloadedThreshold\":7,\"balancedMinThreshold\":3}"))
                .andExpect(status().isUnauthorized());
    }
}
