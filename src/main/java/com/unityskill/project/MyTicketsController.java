package com.unityskill.project;

import com.unityskill.project.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MyTicketsController {

    private final TicketService ticketService;

    @GetMapping("/api/v1/workspaces/{workspaceId}/my-tickets")
    public ResponseEntity<Map<String, Object>> listMyTickets(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        List<TicketResponse> tickets = ticketService.listMyTickets(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", tickets));
    }
}
