package com.unityskill.project;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicProjectController.class)
@Import(SecurityConfig.class)
class PublicProjectControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;

    @Test
    void getPublicProject_withoutAuth_returns404NotUnauthorized() throws Exception {
        // AC3: public endpoint must NOT require JWT — responds with 404, not 401
        mockMvc.perform(get("/api/v1/public/projects/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void getPublicProject_withValidAuth_alsoReturns404() throws Exception {
        // Authenticated users can also access public endpoints without rejection
        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn("550e8400-e29b-41d4-a716-446655440000");

        mockMvc.perform(get("/api/v1/public/projects/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void getPublicProject_invalidUuid_returns400() throws Exception {
        // Spring MVC cannot convert "not-a-uuid" to UUID — returns 400
        mockMvc.perform(get("/api/v1/public/projects/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
