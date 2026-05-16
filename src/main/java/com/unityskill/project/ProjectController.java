package com.unityskill.project;

import com.unityskill.project.dto.CreateProjectRequest;
import com.unityskill.project.dto.ProjectResponse;
import com.unityskill.project.dto.TicketActivityResponse;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TicketActivityService ticketActivityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProject(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal String userId) {
        ProjectResponse project = projectService.createProject(req, workspaceId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", project));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        ProjectResponse project = projectService.getProject(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", project));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listProjects(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean archived,
            @AuthenticationPrincipal String userId) {
        List<ProjectResponse> projects = archived
            ? projectService.listArchivedProjects(workspaceId, UUID.fromString(userId))
            : projectService.listProjects(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", projects));
    }

    @GetMapping("/{projectId}/activities")
    public ResponseEntity<Map<String, Object>> listActivities(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        List<TicketActivityResponse> activities =
            ticketActivityService.listProjectActivities(workspaceId, projectId);
        return ResponseEntity.ok(Map.of("data", activities));
    }

    @PatchMapping("/{projectId}/archive")
    public ResponseEntity<Map<String, Object>> archiveProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        ProjectResponse result = projectService.archiveProject(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }

    @PatchMapping("/{projectId}/unarchive")
    public ResponseEntity<Map<String, Object>> unarchiveProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        ProjectResponse result = projectService.unarchiveProject(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", result));
    }
}
