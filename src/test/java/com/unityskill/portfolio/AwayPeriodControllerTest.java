package com.unityskill.portfolio;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.portfolio.dto.AwayPeriodResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AwayPeriodController.class)
@Import(SecurityConfig.class)
class AwayPeriodControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean AwayPeriodService awayPeriodService;

    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void createAwayPeriod_validRequest_returns201() throws Exception {
        // AC1
        mockValidJwt();
        AwayPeriodResponse response = new AwayPeriodResponse(
                UUID.randomUUID().toString(), USER_ID,
                "2026-04-10", "2026-04-17", "2026-04-20T00:00:00Z");

        when(awayPeriodService.createAwayPeriod(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/me/away-periods")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-04-10\",\"endDate\":\"2026-04-17\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startDate").value("2026-04-10"))
                .andExpect(jsonPath("$.data.endDate").value("2026-04-17"));
    }

    @Test
    void createAwayPeriod_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/away-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-04-10\",\"endDate\":\"2026-04-17\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAwayPeriod_missingStartDate_returns400() throws Exception {
        // @NotNull on startDate → MethodArgumentNotValidException → 400 VALIDATION_FAILED
        mockValidJwt();
        mockMvc.perform(post("/api/v1/users/me/away-periods")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-04-17\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void getAwayPeriods_authenticated_returns200WithList() throws Exception {
        // AC4
        mockValidJwt();
        AwayPeriodResponse r1 = new AwayPeriodResponse(
                UUID.randomUUID().toString(), USER_ID,
                "2026-03-01", "2026-03-07", "2026-03-01T00:00:00Z");
        AwayPeriodResponse r2 = new AwayPeriodResponse(
                UUID.randomUUID().toString(), USER_ID,
                "2026-04-10", "2026-04-17", "2026-04-10T00:00:00Z");

        when(awayPeriodService.getAwayPeriods(any())).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/v1/users/me/away-periods")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data[1].startDate").value("2026-04-10"));
    }
}
