package com.unityskill.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.workspace.dto.CreateWorkspaceRequest;
import com.unityskill.workspace.dto.WorkspaceResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkspaceController.class)
@Import(SecurityConfig.class)
class WorkspaceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean WorkspaceService workspaceService;

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private WorkspaceResponse sampleWorkspace() {
        return new WorkspaceResponse(
                UUID.randomUUID().toString(), "Test Team", "A description",
                "test-team-abcd1234", USER_ID,
                Instant.now().toString(), Instant.now().toString()
        );
    }

    @Test
    void createWorkspace_withValidBody_returns201() throws Exception {
        mockValidJwt();
        when(workspaceService.createWorkspace(any(CreateWorkspaceRequest.class), any(UUID.class)))
                .thenReturn(sampleWorkspace());

        mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkspaceRequest("Test Team", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test Team"));
    }

    @Test
    void createWorkspace_withBlankName_returns400() throws Exception {
        mockValidJwt();

        mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkspaceRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void createWorkspace_withoutAuthHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkspaceRequest("Test Team", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWorkspaces_withAuth_returns200() throws Exception {
        mockValidJwt();
        when(workspaceService.listWorkspaces(any(UUID.class))).thenReturn(List.of(sampleWorkspace()));

        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Test Team"));
    }

    @Test
    void getWorkspace_withAuth_returns200() throws Exception {
        mockValidJwt();
        var ws = sampleWorkspace();
        when(workspaceService.getWorkspace(any(UUID.class), any(UUID.class))).thenReturn(ws);

        mockMvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Team"));
    }

    @Test
    void getWorkspace_notMember_returns404() throws Exception {
        mockValidJwt();
        when(workspaceService.getWorkspace(any(UUID.class), any(UUID.class)))
                .thenThrow(new WorkspaceNotFoundException());

        mockMvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WORKSPACE_NOT_FOUND"));
    }
}
