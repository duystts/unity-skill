package com.unityskill.workspace;

import com.unityskill.workspace.dto.CreateWorkspaceRequest;
import com.unityskill.workspace.dto.WorkspaceResponse;
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
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest req,
            @AuthenticationPrincipal String userId) {
        WorkspaceResponse workspace = workspaceService.createWorkspace(req, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", workspace));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @AuthenticationPrincipal String userId) {
        List<WorkspaceResponse> workspaces = workspaceService.listWorkspaces(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", workspaces));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<Map<String, Object>> getWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        WorkspaceResponse workspace = workspaceService.getWorkspace(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", workspace));
    }
}
