package com.unityskill.privacy;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.privacy.entity.DataExport;
import com.unityskill.privacy.entity.ExportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataExportController.class)
@Import(SecurityConfig.class)
class DataExportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean DataExportRepository dataExportRepository;
    @MockitoBean DataExportService dataExportService;

    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void requestExport_authenticated_returns202Processing() throws Exception {
        // AC1: POST triggers async export and returns 202 PROCESSING
        mockValidJwt();
        when(dataExportRepository.existsByUserIdAndStatus(any(), eq(ExportStatus.IN_PROGRESS)))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/users/me/data-export")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(dataExportService).generateExport(UUID.fromString(USER_ID));
    }

    @Test
    void requestExport_alreadyInProgress_returns202WithInProgressStatus() throws Exception {
        // AC4: if export is running, return 202 with IN_PROGRESS status (no duplicate job)
        mockValidJwt();
        when(dataExportRepository.existsByUserIdAndStatus(any(), eq(ExportStatus.IN_PROGRESS)))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/users/me/data-export")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verifyNoInteractions(dataExportService);  // AC4: no new job started
    }

    @Test
    void downloadExport_authenticated_returnsJsonAttachment() throws Exception {
        // AC2: GET returns JSON file with Content-Disposition attachment header
        mockValidJwt();
        DataExport readyExport = DataExport.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(USER_ID))
                .status(ExportStatus.READY)
                .exportJson("{\"userId\":\"" + USER_ID + "\",\"contributionEvents\":[]}")
                .completedAt(Instant.now())
                .build();
        when(dataExportRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                any(), eq(ExportStatus.READY)))
                .thenReturn(Optional.of(readyExport));

        mockMvc.perform(get("/api/v1/users/me/data-export/download")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"unity-skill-export.json\""))
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    void requestExport_unauthenticated_returns401() throws Exception {
        // Security: no auth header → 401 Unauthorized
        mockMvc.perform(post("/api/v1/users/me/data-export"))
                .andExpect(status().isUnauthorized());
    }
}
