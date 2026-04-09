package com.unityskill.workspace;

import com.unityskill.workspace.dto.MemberResponse;
import com.unityskill.workspace.dto.UpdateMemberRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<Map<String, Object>> listMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        List<MemberResponse> result = memberService.listMembers(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    @PatchMapping("/{workspaceId}/members/{targetUserId}")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal String userId) {
        MemberResponse result = memberService.updateRole(
                workspaceId, targetUserId, request.role(), UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    @DeleteMapping("/{workspaceId}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal String userId) {
        memberService.removeMember(workspaceId, targetUserId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
