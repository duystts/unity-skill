package com.unityskill.auth;

import com.unityskill.auth.dto.PreferencesResponse;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class IncognitoControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean AuthService authService;

    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ─── GET /api/v1/users/me/preferences ────────────────────────────────

    @Test
    void getPreferences_authenticated_returns200WithIncognitoMode() throws Exception {
        // AC4: preferences response includes incognitoMode
        mockValidJwt();
        when(authService.getPreferences(any()))
                .thenReturn(new PreferencesResponse("CHARACTER", false));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uiMode").value("CHARACTER"))
                .andExpect(jsonPath("$.data.incognitoMode").value(false));
    }

    @Test
    void getPreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/v1/users/me/incognito/enable ──────────────────────────

    @Test
    void enableIncognito_authenticated_setsIncognitoTrueAndReturns200() throws Exception {
        // AC1
        mockValidJwt();
        when(authService.enableIncognito(any()))
                .thenReturn(new PreferencesResponse("CHARACTER", true));

        mockMvc.perform(post("/api/v1/users/me/incognito/enable")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incognitoMode").value(true));
    }

    @Test
    void enableIncognito_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/incognito/enable"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/v1/users/me/incognito/disable ─────────────────────────

    @Test
    void disableIncognito_authenticated_setsIncognitoFalseAndReturns200() throws Exception {
        // AC3
        mockValidJwt();
        when(authService.disableIncognito(any()))
                .thenReturn(new PreferencesResponse("CHARACTER", false));

        mockMvc.perform(post("/api/v1/users/me/incognito/disable")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incognitoMode").value(false));
    }

    @Test
    void disableIncognito_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/incognito/disable"))
                .andExpect(status().isUnauthorized());
    }
}
