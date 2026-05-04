package com.unityskill.project.dto;

import com.unityskill.project.entity.AutoTriggerRule;

public record TriggerRuleResponse(
        String id,
        String projectId,
        String workspaceId,
        String triggerType,
        String sourceStageId,
        String targetStageId
) {
    public static TriggerRuleResponse from(AutoTriggerRule r) {
        return new TriggerRuleResponse(
                r.getId().toString(),
                r.getProjectId().toString(),
                r.getWorkspaceId().toString(),
                r.getTriggerType().name(),
                r.getSourceStageId() != null ? r.getSourceStageId().toString() : null,
                r.getTargetStageId().toString()
        );
    }
}
