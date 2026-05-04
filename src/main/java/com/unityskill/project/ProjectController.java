package com.unityskill.project;

import com.unityskill.project.dto.CreateProjectRequest;
import com.unityskill.project.dto.ProjectResponse;
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProject(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal String userId) {
        ProjectResponse project = projectService.createProject(req, workspaceId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", project));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listProjects(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        List<ProjectResponse> projects = projectService.listProjects(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", projects));
    }
}
