package com.unityskill.collaboration;

import com.unityskill.auth.JwtUtil;
import com.unityskill.collaboration.dto.TranscriptResponse;
import com.unityskill.collaboration.entity.TranscriptStatus;
import com.unityskill.common.exception.MeetingNotFoundException;
import com.unityskill.common.exception.UnsupportedFormatException;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TranscriptController.class)
@Import(SecurityConfig.class)
class TranscriptControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TranscriptService transcriptService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID MEETING_ID   = UUID.randomUUID();
    private static final String USER_ID    = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN      = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    private TranscriptResponse sampleResponse() {
        return TranscriptResponse.builder()
                .id(UUID.randomUUID())
                .meetingId(MEETING_ID)
                .workspaceId(WORKSPACE_ID)
                .status(TranscriptStatus.UPLOADED.name())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void upload_validVttFile_returns201() throws Exception {
        mockValidJwt();
        when(transcriptService.uploadTranscript(eq(WORKSPACE_ID), eq(MEETING_ID), any(UUID.class), any()))
                .thenReturn(sampleResponse());

        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.vtt", "text/plain",
                "WEBVTT\n00:00.000 --> 00:01.000\nHello team".getBytes());

        mockMvc.perform(multipart("/api/v1/workspaces/{wId}/meetings/{mId}/transcript",
                        WORKSPACE_ID, MEETING_ID)
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));
    }

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.vtt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/workspaces/{wId}/meetings/{mId}/transcript",
                        WORKSPACE_ID, MEETING_ID)
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_invalidExtension_returns400() throws Exception {
        mockValidJwt();
        when(transcriptService.uploadTranscript(any(), any(), any(), any()))
                .thenThrow(new UnsupportedFormatException("Only .vtt and .txt files are accepted"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "recording.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/workspaces/{wId}/meetings/{mId}/transcript",
                        WORKSPACE_ID, MEETING_ID)
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    void upload_meetingNotFound_returns404() throws Exception {
        mockValidJwt();
        when(transcriptService.uploadTranscript(any(), any(), any(), any()))
                .thenThrow(new MeetingNotFoundException());

        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.vtt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/workspaces/{wId}/meetings/{mId}/transcript",
                        WORKSPACE_ID, MEETING_ID)
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEETING_NOT_FOUND"));
    }

    @Test
    void upload_success_triggersAsyncProcessing() throws Exception {
        mockValidJwt();
        TranscriptResponse response = sampleResponse();
        when(transcriptService.uploadTranscript(eq(WORKSPACE_ID), eq(MEETING_ID), any(UUID.class), any()))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.vtt", "text/plain", "WEBVTT content".getBytes());

        mockMvc.perform(multipart("/api/v1/workspaces/{wId}/meetings/{mId}/transcript",
                        WORKSPACE_ID, MEETING_ID)
                        .file(file)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isCreated());

        verify(transcriptService).processAsync(eq(response.getId()), any(UUID.class), eq(WORKSPACE_ID));
    }
}
