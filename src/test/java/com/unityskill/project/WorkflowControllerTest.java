package com.unityskill.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.project.dto.CreateStageRequest;
import com.unityskill.project.dto.StageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
@Import(SecurityConfig.class)
class WorkflowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private WorkflowService workflowService;
    @MockitoBean private JwtUtil jwtUtil;

    private StageResponse sampleStage(UUID projectId, UUID workspaceId) {
        return new StageResponse(
            UUID.randomUUID().toString(),
            projectId.toString(),
            workspaceId.toString(),
            "In Progress",
            1,
            false,
            "2026-04-11T00:00:00Z"
        );
    }

    @Test
    void createStage_authenticated_returns201() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StageResponse response = sampleStage(projectId, workspaceId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(workflowService.createStage(any(CreateStageRequest.class), eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("name", "In Progress", "position", 1)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("In Progress"));
    }

    @Test
    void createStage_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages",
                        workspaceId, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("name", "In Progress", "position", 1)
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listStages_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(workflowService.listStages(eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(List.of(sampleStage(projectId, workspaceId)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("In Progress"));
    }

    @Test
    void deleteStage_authenticated_returns204() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages/{stageId}",
                        workspaceId, projectId, stageId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
    }
}
