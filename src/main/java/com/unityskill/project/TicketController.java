package com.unityskill.project;

import com.unityskill.project.dto.CreateTicketRequest;
import com.unityskill.project.dto.TicketResponse;
import com.unityskill.project.dto.UpdateTicketRequest;
import com.unityskill.project.entity.AssignmentMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTicket(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTicketRequest req,
            @AuthenticationPrincipal String userId) {
        TicketResponse ticket = ticketService.createTicket(req, workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", ticket));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTickets(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) AssignmentMode mode,
            @AuthenticationPrincipal String userId) {
        List<TicketResponse> tickets = (mode == AssignmentMode.OPEN_POOL)
            ? ticketService.listOpenPoolTickets(workspaceId, projectId, UUID.fromString(userId))
            : ticketService.listTickets(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", tickets));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<Map<String, Object>> updateTicket(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ticketId,
            @RequestBody UpdateTicketRequest req,
            @AuthenticationPrincipal String userId) {
        TicketResponse ticket = ticketService.updateTicket(req, workspaceId, projectId, ticketId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", ticket));
    }

    @PostMapping("/{ticketId}/claim")
    public ResponseEntity<Map<String, Object>> claimTicket(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal String userId) {
        TicketResponse ticket = ticketService.claimTicket(workspaceId, projectId, ticketId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", ticket));
    }
}
