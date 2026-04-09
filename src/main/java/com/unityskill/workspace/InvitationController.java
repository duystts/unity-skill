package com.unityskill.workspace;

import com.unityskill.workspace.dto.InviteByEmailRequest;
import com.unityskill.workspace.dto.InvitationResponse;
import com.unityskill.workspace.dto.InviteLinkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/api/v1/workspaces/{workspaceId}/invitations")
    public ResponseEntity<Map<String, Object>> inviteByEmail(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody InviteByEmailRequest request,
            @AuthenticationPrincipal String userId) {
        InvitationResponse result = invitationService.inviteByEmail(
                workspaceId, request.email(), UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/invite-link")
    public ResponseEntity<Map<String, Object>> generateInviteLink(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        InviteLinkResponse result = invitationService.generateInviteLink(
                workspaceId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", result));
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    public ResponseEntity<Map<String, Object>> acceptInvitation(
            @PathVariable String token,
            @AuthenticationPrincipal String userId) {
        InvitationResponse result = invitationService.acceptInvitation(token, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }
}
