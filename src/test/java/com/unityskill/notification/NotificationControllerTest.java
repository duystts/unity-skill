package com.unityskill.notification;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    void getNotifications_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationResponse item = new NotificationResponse(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "TEST_EVENT",
            "{}",
            false,
            "2026-04-10T00:00:00Z"
        );

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(notificationService.getNotifications(eq(userId), eq(0), eq(50)))
            .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.total").value(1));
    }

    @Test
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markRead_authenticated_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        doNothing().when(notificationService).markRead(eq(notificationId), eq(userId));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNoContent());
    }
}
