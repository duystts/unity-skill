package com.unityskill.project;

import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.project.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MyTicketsController.class)
@Import(SecurityConfig.class)
class MyTicketsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TicketService ticketService;
    @MockitoBean private JwtUtil jwtUtil;

    private TicketResponse sampleTicket(UUID workspaceId) {
        return new TicketResponse(
            UUID.randomUUID().toString(),
            workspaceId.toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "My task",
            null,
            UUID.randomUUID().toString(),
            "ASSIGNED",
            null,
            null,
            "2026-01-01T00:00:00Z",
            "2026-01-01T00:00:00Z"
        );
    }

    @Test
    void listMyTickets_authenticated_returns200() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TicketResponse response = sampleTicket(workspaceId);

        when(jwtUtil.isTokenValid("test-token")).thenReturn(true);
        when(jwtUtil.extractUserId("test-token")).thenReturn(userId.toString());
        when(ticketService.listMyTickets(eq(workspaceId), eq(userId)))
            .thenReturn(List.of(response));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/my-tickets", workspaceId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("My task"));
    }

    @Test
    void listMyTickets_unauthenticated_returns401() throws Exception {
        UUID workspaceId = UUID.randomUUID();

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/my-tickets", workspaceId))
                .andExpect(status().isUnauthorized());
    }
}
