package com.unityskill.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.exception.LastAdminException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.workspace.dto.MemberResponse;
import com.unityskill.workspace.dto.UpdateMemberRoleRequest;
import com.unityskill.workspace.entity.WorkspaceRole;
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

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean MemberService memberService;

    private static final String USER_ID    = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TARGET_ID  = "660e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN      = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final UUID   WORKSPACE_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440000");

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private MemberResponse sampleMember(String role) {
        return new MemberResponse(TARGET_ID, "dev@example.com", "Dev User", role, Instant.now().toString());
    }

    // ─── GET /members ───

    @Test
    void listMembers_withAuth_returns200() throws Exception {
        mockValidJwt();
        when(memberService.listMembers(any(), any())).thenReturn(List.of(sampleMember("DEVELOPER")));

        mockMvc.perform(get("/api/v1/workspaces/{id}/members", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].email").value("dev@example.com"));
    }

    @Test
    void listMembers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/members", WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMembers_notMember_returns404() throws Exception {
        mockValidJwt();
        when(memberService.listMembers(any(), any())).thenThrow(new WorkspaceNotFoundException());

        mockMvc.perform(get("/api/v1/workspaces/{id}/members", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WORKSPACE_NOT_FOUND"));
    }

    // ─── PATCH /members/{userId} ───

    @Test
    void updateRole_success_returns200() throws Exception {
        mockValidJwt();
        when(memberService.updateRole(any(), any(), any(), any())).thenReturn(sampleMember("PM"));

        mockMvc.perform(patch("/api/v1/workspaces/{id}/members/{uid}", WORKSPACE_ID, TARGET_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMemberRoleRequest(WorkspaceRole.PM))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PM"));
    }

    @Test
    void updateRole_notAdmin_returns403() throws Exception {
        mockValidJwt();
        when(memberService.updateRole(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedAccessException("Access denied: Admin role required"));

        mockMvc.perform(patch("/api/v1/workspaces/{id}/members/{uid}", WORKSPACE_ID, TARGET_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMemberRoleRequest(WorkspaceRole.PM))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ─── DELETE /members/{userId} ───

    @Test
    void removeMember_success_returns204() throws Exception {
        mockValidJwt();
        doNothing().when(memberService).removeMember(any(), any(), any());

        mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{uid}", WORKSPACE_ID, TARGET_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeMember_lastAdmin_returns400() throws Exception {
        mockValidJwt();
        doThrow(new LastAdminException()).when(memberService).removeMember(any(), any(), any());

        mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{uid}", WORKSPACE_ID, TARGET_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("LAST_ADMIN"))
                .andExpect(jsonPath("$.message").value("Cannot remove the only Admin"));
    }
}
