package com.unityskill.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.dto.AuthUserResponse;
import com.unityskill.auth.entity.UiMode;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class PreferencesControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserRepository userRepository;

    @Test
    void updatePreferences_withValidBody_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = new AuthUserResponse(userId, "a@b.com", "A", "SERIOUS");

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(authService.updatePreferences(eq(userId), eq(UiMode.SERIOUS))).thenReturn(user);

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("uiMode", "SERIOUS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uiMode").value("SERIOUS"));
    }

    @Test
    void updatePreferences_invalidUiMode_returns400() throws Exception {
        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(UUID.randomUUID().toString());

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiMode\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("uiMode", "SERIOUS"))))
                .andExpect(status().isUnauthorized());
    }
}
