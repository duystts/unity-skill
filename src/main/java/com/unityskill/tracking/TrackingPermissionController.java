package com.unityskill.tracking;

import com.unityskill.tracking.dto.TrackingPermissionRequest;
import com.unityskill.tracking.entity.TrackingPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class TrackingPermissionController {

    private final TrackingPermissionService trackingPermissionService;

    /**
     * POST /api/v1/workspaces/{workspaceId}/users/me/tracking-permissions
     * AC1/2: Create or update a tracking permission.
     *
     * Note: epics spec uses /api/v1/users/me/tracking-permissions but tracking permissions
     * are workspace-scoped (tracking_permissions.workspace_id NOT NULL). Using workspace-scoped
     * path matches the project's URL conventions for workspace resources.
     */
    @PostMapping("/api/v1/workspaces/{workspaceId}/users/me/tracking-permissions")
    public ResponseEntity<Map<String, Object>> setPermission(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody TrackingPermissionRequest request,
            @AuthenticationPrincipal String userId) {
        TrackingPermission perm = trackingPermissionService.setPermission(
                UUID.fromString(userId), workspaceId,
                request.resourceType(), request.resourceId(), request.enabled());
        return ResponseEntity.ok(Map.of("data", toDto(perm)));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/users/me/tracking-permissions
     * AC3: List all tracking permissions for the authenticated developer in this workspace.
     */
    @GetMapping("/api/v1/workspaces/{workspaceId}/users/me/tracking-permissions")
    public ResponseEntity<Map<String, Object>> listPermissions(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        List<TrackingPermission> perms = trackingPermissionService.listPermissions(
                UUID.fromString(userId), workspaceId);
        // AC3: group by resource type
        Map<String, List<Map<String, Object>>> grouped = perms.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getResourceType().name(),
                        Collectors.mapping(this::toDto, Collectors.toList())
                ));
        return ResponseEntity.ok(Map.of("data", grouped));
    }

    private Map<String, Object> toDto(TrackingPermission p) {
        return Map.of(
                "id",           p.getId().toString(),
                "resourceType", p.getResourceType().name(),
                "resourceId",   p.getResourceId(),
                "enabled",      p.isEnabled(),
                "updatedAt",    p.getUpdatedAt().toString()
        );
    }
}
