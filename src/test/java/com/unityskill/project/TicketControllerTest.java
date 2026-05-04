package com.unityskill.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.project.dto.CreateTicketRequest;
import com.unityskill.project.dto.TicketResponse;
import com.unityskill.project.dto.UpdateTicketRequest;
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

import com.unityskill.project.entity.AssignmentMode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TicketService ticketService;
    @MockitoBean private JwtUtil jwtUtil;

    private TicketResponse sampleTicket(UUID workspaceId, UUID projectId) {
        return new TicketResponse(
            UUID.randomUUID().toString(),
            workspaceId.toString(),
            projectId.toString(),
            UUID.randomUUID().toString(),
            "Fix login bug",
            null,
            null,
            "NONE",
            null,
            null,
            "2026-01-01T00:00:00Z",
            "2026-01-01T00:00:00Z"
        );
    }

    @Test
    void createTicket_authenticated_returns201() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId, projectId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.createTicket(any(), eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            Map.of("title", "Fix login bug", "stageId", stageId.toString())
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Fix login bug"));
    }

    @Test
    void createTicket_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets",
                        workspaceId, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Test"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTickets_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId, projectId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.listTickets(eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(List.of(response));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets",
                        workspaceId, projectId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Fix login bug"));
    }

    @Test
    void listTickets_withModeParam_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId, projectId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.listOpenPoolTickets(eq(workspaceId), eq(projectId), eq(userId)))
            .thenReturn(List.of(response));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets",
                        workspaceId, projectId)
                        .param("mode", "OPEN_POOL")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Fix login bug"));
    }

    @Test
    void claimTicket_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId, projectId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.claimTicket(eq(workspaceId), eq(projectId), eq(ticketId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets/{ticketId}/claim",
                        workspaceId, projectId, ticketId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fix login bug"));
    }

    @Test
    void updateTicket_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId, projectId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.updateTicket(any(), eq(workspaceId), eq(projectId), eq(ticketId), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets/{ticketId}",
                        workspaceId, projectId, ticketId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Updated title"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Fix login bug"));
    }
}
