package com.unityskill.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.dto.AuthUserResponse;
import com.unityskill.auth.dto.LoginRequest;
import com.unityskill.auth.dto.LoginResponse;
import com.unityskill.auth.dto.RegisterRequest;
import com.unityskill.auth.dto.RegisterResponse;
import com.unityskill.common.exception.InvalidCredentialsException;
import com.unityskill.common.exception.TokenInactiveException;
import com.unityskill.common.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;
    @MockitoBean private JwtUtil jwtUtil; // required: SecurityConfig constructor-injects JwtUtil

    // ── Register ──────────────────────────────────────────────────────────────

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("not-an-email", "password123", "User"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("test@example.com", "short", "User"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        var user = new AuthUserResponse(UUID.randomUUID(), "test@example.com", "Test User", "CHARACTER");
        var result = new AuthService.RegistrationResult(new RegisterResponse(user, "jwt-token"), "raw-token");
        when(authService.register(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("test@example.com", "password123", "Test User"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.accessToken").value("jwt-token"));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200() throws Exception {
        var user = new AuthUserResponse(UUID.randomUUID(), "test@example.com", "Test User", "CHARACTER");
        var result = new AuthService.LoginResult(new LoginResponse(user, "jwt-token"), "raw-token");
        when(authService.login(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("test@example.com", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.accessToken").value("jwt-token"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("test@example.com", "wrongpass"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200() throws Exception {
        when(authService.refreshAccessToken("raw-token")).thenReturn("new-jwt-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "raw-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("new-jwt-token"));
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("TOKEN_INACTIVE"));
    }

    @Test
    void refresh_inactiveCookie_returns401() throws Exception {
        when(authService.refreshAccessToken("stale-token")).thenThrow(new TokenInactiveException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "stale-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("TOKEN_INACTIVE"));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refreshToken", "raw-token")))
            .andExpect(status().isNoContent());
    }

    // ── Me ────────────────────────────────────────────────────────────────────

    @Test
    void me_withValidToken_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = new AuthUserResponse(userId, "test@example.com", "Test User", "CHARACTER");

        when(jwtUtil.isTokenValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(userId.toString());
        when(authService.getMe(userId)).thenReturn(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void me_withInvalidToken_returns401() throws Exception {
        when(jwtUtil.isTokenValid("expired-token")).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer expired-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("TOKEN_EXPIRED"));
    }

    @Test
    void me_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}
