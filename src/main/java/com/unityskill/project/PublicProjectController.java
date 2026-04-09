package com.unityskill.project;

import com.unityskill.common.exception.ProjectNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/projects")
public class PublicProjectController {

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getPublicProject(@PathVariable UUID projectId) {
        // Stub: Project entity and visibility logic implemented in Epic 3.
        // SecurityConfig permits this endpoint without JWT — AC3 satisfied.
        throw new ProjectNotFoundException();
    }
}
