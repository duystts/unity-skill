package com.unityskill.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.project.dto.CreateProjectRequest;
import com.unityskill.project.dto.ProjectResponse;
import com.unityskill.project.entity.ProjectVisibility;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
@Import(SecurityConfig.class)
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private JwtUtil jwtUtil;

    private ProjectResponse sampleResponse(UUID workspaceId) {
        return new ProjectResponse(
            UUID.randomUUID().toString(),
            workspaceId.toString(),
            "Test Project",
            null,
            "PRIVATE",
            "2026-04-10T00:00:00Z"
        );
    }

    @Test
    void createProject_authenticated_returns201() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectResponse response = sampleResponse(workspaceId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(projectService.createProject(any(CreateProjectRequest.class), eq(workspaceId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects", workspaceId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("name", "Test Project", "visibility", "PRIVATE")
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test Project"));
    }

    @Test
    void createProject_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("name", "Test Project", "visibility", "PRIVATE")
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProjects_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(projectService.listProjects(eq(workspaceId), eq(userId)))
            .thenReturn(List.of(sampleResponse(workspaceId)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/projects", workspaceId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Test Project"));
    }
}
