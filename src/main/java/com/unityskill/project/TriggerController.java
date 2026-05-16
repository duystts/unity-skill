package com.unityskill.project;

import com.unityskill.project.dto.CreateTriggerRuleRequest;
import com.unityskill.project.dto.TriggerRuleResponse;
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
@RequiredArgsConstructor
public class TriggerController {

    private final TriggerService triggerService;

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/stages/{stageId}/triggers")
    public ResponseEntity<Map<String, Object>> createRule(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID stageId,
            @Valid @RequestBody CreateTriggerRuleRequest req,
            @AuthenticationPrincipal String userId) {
        TriggerRuleResponse rule = triggerService.createRule(req, workspaceId, projectId, stageId, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", rule));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/triggers")
    public ResponseEntity<Map<String, Object>> listRules(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal String userId) {
        List<TriggerRuleResponse> rules = triggerService.listRules(workspaceId, projectId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", rules));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/triggers/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal String userId) {
        triggerService.deleteRule(workspaceId, projectId, ruleId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
