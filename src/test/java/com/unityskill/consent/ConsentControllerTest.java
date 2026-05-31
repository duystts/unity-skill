package com.unityskill.consent;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.consent.entity.ConsentRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConsentController.class)
@Import(SecurityConfig.class)
class ConsentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean ConsentService consentService;

    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void recordConsent_authenticated_returns200WithTimestamp() throws Exception {
        // AC3: POST creates ConsentRecord and returns consentedAt
        mockValidJwt();
        ConsentRecord record = ConsentRecord.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(USER_ID))
                .consentedAt(Instant.parse("2026-04-24T08:00:00Z"))
                .consentVersion("1.0")
                .build();
        when(consentService.recordConsent(any())).thenReturn(record);

        mockMvc.perform(post("/api/v1/users/me/consent")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consentedAt").value("2026-04-24T08:00:00Z"))
                .andExpect(jsonPath("$.data.consentVersion").value("1.0"));
    }

    @Test
    void recordConsent_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consent"))
                .andExpect(status().isUnauthorized());
    }
}
