package com.unityskill.auth;

import com.unityskill.attachment.CloudinaryService;
import com.unityskill.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PortfolioUrlControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean AuthService authService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AccountSettingsService accountSettingsService;
    @MockitoBean CloudinaryService cloudinaryService;

    private static final String USER_ID     = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TOKEN       = "test-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    private void mockValidJwt() {
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void getPortfolioUrl_authenticated_returns200WithUrl() throws Exception {
        // AC4: returns full shareable portfolio URL using default frontend-url
        mockValidJwt();

        mockMvc.perform(get("/api/v1/users/me/portfolio-url")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioUrl")
                        .value("http://localhost:3000/portfolio/" + USER_ID));
    }

    @Test
    void getPortfolioUrl_unauthenticated_returns401() throws Exception {
        // No Authorization header — endpoint requires auth
        mockMvc.perform(get("/api/v1/users/me/portfolio-url"))
                .andExpect(status().isUnauthorized());
    }
}
