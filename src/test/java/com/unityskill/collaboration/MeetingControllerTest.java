package com.unityskill.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.collaboration.dto.MeetingResponse;
import com.unityskill.collaboration.entity.MeetingStatus;
import com.unityskill.common.exception.MeetingNotFoundException;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeetingController.class)
@Import(SecurityConfig.class)
class MeetingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean MeetingService meetingService;
    @MockitoBean AgendaService agendaService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String USER_ID    = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN      = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private MeetingResponse sampleMeeting() {
        return MeetingResponse.builder()
                .id(UUID.randomUUID())
                .workspaceId(WORKSPACE_ID)
                .projectId(UUID.randomUUID())
                .title("Sprint Planning")
                .scheduledAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .status(MeetingStatus.SCHEDULED.name())
                .createdBy(UUID.fromString(USER_ID))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void postMeeting_validRequest_returns201WithData() throws Exception {
        mockValidJwt();
        when(meetingService.createMeeting(eq(WORKSPACE_ID), any(UUID.class), any()))
                .thenReturn(sampleMeeting());

        String body = """
                {"title":"Sprint Planning","projectId":"%s","scheduledAt":"2026-05-01T10:00:00Z"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/workspaces/{wId}/meetings", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Sprint Planning"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void postMeeting_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{wId}/meetings", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Meeting\",\"projectId\":\"" + UUID.randomUUID()
                                + "\",\"scheduledAt\":\"2026-05-01T10:00:00Z\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeetings_withAuth_returns200WithList() throws Exception {
        mockValidJwt();
        when(meetingService.listMeetings(eq(WORKSPACE_ID), any(UUID.class)))
                .thenReturn(List.of(sampleMeeting()));

        mockMvc.perform(get("/api/v1/workspaces/{wId}/meetings", WORKSPACE_ID)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("Sprint Planning"));
    }

    @Test
    void patchMeeting_validUpdate_returns200() throws Exception {
        mockValidJwt();
        UUID meetingId = UUID.randomUUID();
        when(meetingService.updateMeeting(eq(WORKSPACE_ID), eq(meetingId), any(UUID.class), any()))
                .thenReturn(sampleMeeting());

        mockMvc.perform(patch("/api/v1/workspaces/{wId}/meetings/{mId}",
                        WORKSPACE_ID, meetingId)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Sprint Planning"));
    }

    @Test
    void patchMeeting_notFound_returns404() throws Exception {
        mockValidJwt();
        UUID meetingId = UUID.randomUUID();
        when(meetingService.updateMeeting(eq(WORKSPACE_ID), eq(meetingId), any(UUID.class), any()))
                .thenThrow(new MeetingNotFoundException());

        mockMvc.perform(patch("/api/v1/workspaces/{wId}/meetings/{mId}",
                        WORKSPACE_ID, meetingId)
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEETING_NOT_FOUND"));
    }

    // ── GET /{meetingId} ──────────────────────────────────────────────────

    @Test
    void getMeeting_authenticated_returns200() throws Exception {
        mockValidJwt();
        UUID meetingId = UUID.randomUUID();
        when(meetingService.getMeeting(eq(WORKSPACE_ID), eq(meetingId), any(UUID.class)))
                .thenReturn(sampleMeeting());

        mockMvc.perform(get("/api/v1/workspaces/{wId}/meetings/{mId}", WORKSPACE_ID, meetingId)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Sprint Planning"));
    }

    @Test
    void getMeeting_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{wId}/meetings/{mId}", WORKSPACE_ID, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{meetingId}/agenda/generate ─────────────────────────────────

    @Test
    void generateAgenda_pmRole_returns202() throws Exception {
        mockValidJwt();
        UUID meetingId = UUID.randomUUID();
        doNothing().when(meetingService).markAgendaGenerating(eq(WORKSPACE_ID), eq(meetingId), any(UUID.class));
        doNothing().when(agendaService).executeGeneration(any(), any(), any());

        mockMvc.perform(post("/api/v1/workspaces/{wId}/meetings/{mId}/agenda/generate",
                        WORKSPACE_ID, meetingId)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Agenda generation started"));
    }

    @Test
    void generateAgenda_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{wId}/meetings/{mId}/agenda/generate",
                        WORKSPACE_ID, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
