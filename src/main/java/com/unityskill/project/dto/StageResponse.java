package com.unityskill.project.dto;

import com.unityskill.project.entity.WorkflowStage;

public record StageResponse(
        String id,
        String projectId,
        String workspaceId,
        String name,
        int position,
        boolean isClosedState,
        String createdAt
) {
    public static StageResponse from(WorkflowStage s) {
        return new StageResponse(
                s.getId().toString(),
                s.getProjectId().toString(),
                s.getWorkspaceId().toString(),
                s.getName(),
                s.getPosition(),
                s.isClosedState(),
                s.getCreatedAt().toString()
        );
    }
}
