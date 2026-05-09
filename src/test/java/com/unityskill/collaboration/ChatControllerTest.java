package com.unityskill.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.collaboration.dto.ChatMessageResponse;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean ChatService chatService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID   = UUID.randomUUID();
    private static final String USER_ID    = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN      = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private ChatMessageResponse sampleResponse() {
        return ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .content("Hello team")
                .senderName("Alice")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void postChat_validRequest_returns201WithData() throws Exception {
        mockValidJwt();
        when(chatService.sendMessage(eq(WORKSPACE_ID), eq(PROJECT_ID), any(UUID.class), eq("Hello team")))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/chat",
                        WORKSPACE_ID, PROJECT_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello team\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Hello team"))
                .andExpect(jsonPath("$.data.senderName").value("Alice"));
    }

    @Test
    void postChat_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/chat",
                        WORKSPACE_ID, PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postChat_nonMember_returns403() throws Exception {
        mockValidJwt();
        when(chatService.sendMessage(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Access denied: not a workspace member"));

        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/chat",
                        WORKSPACE_ID, PROJECT_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getChat_withPagination_returns200WithDataAndPagination() throws Exception {
        mockValidJwt();
        when(chatService.getMessages(eq(WORKSPACE_ID), eq(PROJECT_ID), any(UUID.class), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse())));

        mockMvc.perform(get("/api/v1/workspaces/{wId}/projects/{pId}/chat?page=0&size=50",
                        WORKSPACE_ID, PROJECT_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].content").value("Hello team"))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.total").exists());
    }

    @Test
    void getChat_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{wId}/projects/{pId}/chat",
                        WORKSPACE_ID, PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }
}
