package com.unityskill.project;

import com.unityskill.project.dto.ProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/projects")
@RequiredArgsConstructor
public class PublicProjectController {

    private final ProjectService projectService;

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getPublicProject(@PathVariable UUID projectId) {
        ProjectResponse project = projectService.getPublicProject(projectId);
        return ResponseEntity.ok(Map.of("data", project, "tickets", List.of()));
    }
}
