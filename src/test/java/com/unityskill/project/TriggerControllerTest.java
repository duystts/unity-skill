package com.unityskill.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.project.dto.CreateTriggerRuleRequest;
import com.unityskill.project.dto.TriggerRuleResponse;
import com.unityskill.project.entity.TriggerType;
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

@WebMvcTest(TriggerController.class)
@Import(SecurityConfig.class)
class TriggerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TriggerService triggerService;
    @MockitoBean private JwtUtil jwtUtil;

    private TriggerRuleResponse sampleRule(UUID projectId, UUID sourceStageId, UUID targetStageId) {
        return new TriggerRuleResponse(
            UUID.randomUUID().toString(),
            projectId.toString(),
            UUID.randomUUID().toString(),
            "PR_OPENED",
            sourceStageId.toString(),
            targetStageId.toString()
        );
    }

    @Test
    void createRule_authenticated_returns201() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TriggerRuleResponse response = sampleRule(projectId, stageId, targetStageId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(triggerService.createRule(
            any(CreateTriggerRuleRequest.class),
            eq(workspaceId), eq(projectId), eq(stageId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages/{stageId}/triggers",
                        workspaceId, projectId, stageId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("triggerType", "PR_OPENED", "targetStageId", targetStageId.toString())
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.triggerType").value("PR_OPENED"));
    }

    @Test
    void createRule_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages/{stageId}/triggers",
                        workspaceId, projectId, stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("triggerType", "PR_OPENED", "targetStageId", targetStageId.toString())
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRules_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TriggerRuleResponse response = sampleRule(projectId, UUID.randomUUID(), UUID.randomUUID());

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(triggerService.listRules(eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(List.of(response));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/triggers",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].triggerType").value("PR_OPENED"));
    }
}
