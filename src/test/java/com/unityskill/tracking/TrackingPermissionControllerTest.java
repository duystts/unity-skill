package com.unityskill.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.tracking.dto.TrackingPermissionRequest;
import com.unityskill.tracking.entity.TrackingPermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrackingPermissionController.class)
@Import(SecurityConfig.class)
class TrackingPermissionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TrackingPermissionService trackingPermissionService;

    private static final String TOKEN        = "test-token";
    private static final String AUTH_HEADER  = "Bearer " + TOKEN;
    private static final String USER_ID      = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID   WORKSPACE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void setPermission_authenticated_returns200() throws Exception {
        // AC1: POST creates/updates a permission record
        mockValidJwt();
        TrackingPermission saved = TrackingPermission.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(USER_ID))
                .workspaceId(WORKSPACE_ID)
                .resourceType(ResourceType.GITHUB_REPO)
                .resourceId("owner/repo")
                .enabled(false)
                .updatedAt(Instant.parse("2026-04-25T09:00:00Z"))
                .build();
        when(trackingPermissionService.setPermission(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(saved);

        TrackingPermissionRequest req =
                new TrackingPermissionRequest(ResourceType.GITHUB_REPO, "owner/repo", false);

        mockMvc.perform(post("/api/v1/workspaces/{wsId}/users/me/tracking-permissions", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceType").value("GITHUB_REPO"))
                .andExpect(jsonPath("$.data.resourceId").value("owner/repo"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void listPermissions_authenticated_returnsGrouped() throws Exception {
        // AC3: GET returns permissions grouped by resourceType
        mockValidJwt();
        TrackingPermission repoPermission = TrackingPermission.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(USER_ID))
                .workspaceId(WORKSPACE_ID)
                .resourceType(ResourceType.GITHUB_REPO)
                .resourceId("owner/repo")
                .enabled(true)
                .updatedAt(Instant.parse("2026-04-25T09:00:00Z"))
                .build();
        when(trackingPermissionService.listPermissions(any(), any()))
                .thenReturn(List.of(repoPermission));

        mockMvc.perform(get("/api/v1/workspaces/{wsId}/users/me/tracking-permissions", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.GITHUB_REPO[0].resourceId").value("owner/repo"))
                .andExpect(jsonPath("$.data.GITHUB_REPO[0].enabled").value(true));
    }

    @Test
    void setPermission_unauthenticated_returns401() throws Exception {
        TrackingPermissionRequest req =
                new TrackingPermissionRequest(ResourceType.GITHUB_REPO, "owner/repo", false);

        mockMvc.perform(post("/api/v1/workspaces/{wsId}/users/me/tracking-permissions", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
