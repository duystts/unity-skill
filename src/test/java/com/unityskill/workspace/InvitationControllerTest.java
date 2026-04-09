package com.unityskill.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.AlreadyMemberException;
import com.unityskill.common.exception.InvitationNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.workspace.dto.InviteByEmailRequest;
import com.unityskill.workspace.dto.InvitationResponse;
import com.unityskill.workspace.dto.InviteLinkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvitationController.class)
@Import(SecurityConfig.class)
class InvitationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean InvitationService invitationService;

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final UUID WORKSPACE_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private InvitationResponse sampleInvitationResponse() {
        return new InvitationResponse(
                UUID.randomUUID().toString(),
                WORKSPACE_ID.toString(),
                "user@example.com",
                "abc123token456def789",
                "EMAIL",
                "PENDING",
                Instant.now().plusSeconds(604800).toString(),
                Instant.now().toString()
        );
    }

    // ─── POST /api/v1/workspaces/{id}/invitations ───

    @Test
    void inviteByEmail_success_returns201() throws Exception {
        mockValidJwt();
        when(invitationService.inviteByEmail(any(), any(), any()))
                .thenReturn(sampleInvitationResponse());

        mockMvc.perform(post("/api/v1/workspaces/{id}/invitations", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteByEmailRequest("user@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.type").value("EMAIL"));
    }

    @Test
    void inviteByEmail_invalidEmail_returns400() throws Exception {
        mockValidJwt();

        mockMvc.perform(post("/api/v1/workspaces/{id}/invitations", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteByEmailRequest("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void inviteByEmail_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{id}/invitations", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteByEmailRequest("user@example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inviteByEmail_notAdmin_returns403() throws Exception {
        mockValidJwt();
        when(invitationService.inviteByEmail(any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Access denied: Admin role required"));

        mockMvc.perform(post("/api/v1/workspaces/{id}/invitations", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteByEmailRequest("user@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ─── POST /api/v1/workspaces/{id}/invite-link ───

    @Test
    void generateInviteLink_success_returns201() throws Exception {
        mockValidJwt();
        when(invitationService.generateInviteLink(any(), any()))
                .thenReturn(new InviteLinkResponse(
                        "abc123token456def789",
                        "http://localhost:3000/invite/abc123token456def789",
                        Instant.now().plusSeconds(604800).toString()
                ));

        mockMvc.perform(post("/api/v1/workspaces/{id}/invite-link", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").value("abc123token456def789"))
                .andExpect(jsonPath("$.data.inviteUrl").value("http://localhost:3000/invite/abc123token456def789"));
    }

    // ─── POST /api/v1/invitations/{token}/accept ───

    @Test
    void acceptInvitation_success_returns200() throws Exception {
        mockValidJwt();
        InvitationResponse accepted = new InvitationResponse(
                UUID.randomUUID().toString(),
                WORKSPACE_ID.toString(),
                null,
                "abc123token456def789",
                "LINK",
                "ACCEPTED",
                Instant.now().plusSeconds(604800).toString(),
                Instant.now().toString()
        );
        when(invitationService.acceptInvitation(eq("abc123token456def789"), any()))
                .thenReturn(accepted);

        mockMvc.perform(post("/api/v1/invitations/{token}/accept", "abc123token456def789")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.workspaceId").value(WORKSPACE_ID.toString()));
    }

    @Test
    void acceptInvitation_alreadyMember_returns409() throws Exception {
        mockValidJwt();
        when(invitationService.acceptInvitation(any(), any()))
                .thenThrow(new AlreadyMemberException());

        mockMvc.perform(post("/api/v1/invitations/{token}/accept", "sometoken")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_MEMBER"))
                .andExpect(jsonPath("$.message").value("Already a member"));
    }
}
