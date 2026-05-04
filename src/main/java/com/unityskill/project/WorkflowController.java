package com.unityskill.project;

import com.unityskill.project.dto.CreateStageRequest;
import com.unityskill.project.dto.StageResponse;
import com.unityskill.project.dto.UpdateStageRequest;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createStage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateStageRequest req,
            @AuthenticationPrincipal String userId) {
        StageResponse stage = workflowService.createStage(req, workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", stage));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listStages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        List<StageResponse> stages = workflowService.listStages(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", stages));
    }

    @PutMapping("/{stageId}")
    public ResponseEntity<Map<String, Object>> updateStage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @RequestBody UpdateStageRequest req,
            @AuthenticationPrincipal String userId) {
        StageResponse stage = workflowService.updateStage(req, workspaceId, projectId, stageId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", stage));
    }

    @DeleteMapping("/{stageId}")
    public ResponseEntity<Void> deleteStage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @AuthenticationPrincipal String userId) {
        workflowService.deleteStage(workspaceId, projectId, stageId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
