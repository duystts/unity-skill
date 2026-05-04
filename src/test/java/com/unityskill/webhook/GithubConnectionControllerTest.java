package com.unityskill.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.webhook.dto.GithubConnectionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {GithubConnectionController.class, GithubOAuthController.class})
@Import(SecurityConfig.class)
class GithubConnectionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GithubConnectionService githubConnectionService;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    void getConnection_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(githubConnectionService.getConnection(eq(workspaceId), eq(projectId), eq(userId)))
                .thenReturn(new GithubConnectionResponse(true, "owner/repo"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/projects/{projectId}/github",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.repoFullName").value("owner/repo"));
    }

    @Test
    void getConnection_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/projects/{projectId}/github",
                        workspaceId, projectId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWebhook_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/projects/{projectId}/github/webhook",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("repoFullName", "owner/repo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Webhook registered successfully"));
    }

    @Test
    void initiateOAuth_authenticated_returns200WithAuthUrl() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(githubConnectionService.buildAuthorizationUrl(eq(projectId), eq(workspaceId), any()))
                .thenReturn("https://github.com/login/oauth/authorize?client_id=test");

        mockMvc.perform(get("/api/v1/auth/github")
                        .param("projectId", projectId.toString())
                        .param("workspaceId", workspaceId.toString())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authUrl").value("https://github.com/login/oauth/authorize?client_id=test"));
    }

    @Test
    void handleCallback_publicEndpoint_returns302() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        String state = java.util.Base64.getEncoder().encodeToString(
                (projectId + "|" + workspaceId + "|" + callerId).getBytes());

        when(githubConnectionService.handleCallback(eq("auth-code"), eq(state)))
                .thenReturn("http://localhost:3000/" + workspaceId + "/settings/github?connected=true");

        mockMvc.perform(get("/api/v1/auth/github/callback")
                        .param("code", "auth-code")
                        .param("state", state))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "http://localhost:3000/" + workspaceId + "/settings/github?connected=true"));
    }
}
