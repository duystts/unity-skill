package com.unityskill.privacy;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataDeletionController.class)
@Import(SecurityConfig.class)
class DataDeletionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean DataDeletionService dataDeletionService;

    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void deleteContributionData_authenticated_returns200WithCounts() throws Exception {
        // AC1/4: DELETE returns 200 with deletion report
        mockValidJwt();
        when(dataDeletionService.deleteAllContributionData(UUID.fromString(USER_ID)))
                .thenReturn(Map.of(
                        "contributionEvents", 5, "skillEvidences", 2,
                        "contributionStreaks", 1, "awayPeriods", 1,
                        "endorsements", 3, "trackingPermissions", 2
                ));

        mockMvc.perform(delete("/api/v1/users/me/contribution-data")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedRecords.contributionEvents").value(5))
                .andExpect(jsonPath("$.data.deletedRecords.skillEvidences").value(2))
                .andExpect(jsonPath("$.data.deletedRecords.endorsements").value(3));
    }

    @Test
    void deleteContributionData_unauthenticated_returns401() throws Exception {
        // Security: no Authorization header → 401 Unauthorized
        mockMvc.perform(delete("/api/v1/users/me/contribution-data"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteContributionData_idempotent_returns200WithZeroCounts() throws Exception {
        // AC5: already-deleted user gets 200 with zeros, not 404 or error
        mockValidJwt();
        when(dataDeletionService.deleteAllContributionData(any()))
                .thenReturn(Map.of(
                        "contributionEvents", 0, "skillEvidences", 0,
                        "contributionStreaks", 0, "awayPeriods", 0,
                        "endorsements", 0, "trackingPermissions", 0
                ));

        mockMvc.perform(delete("/api/v1/users/me/contribution-data")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedRecords.contributionEvents").value(0));
    }
}
