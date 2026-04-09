package com.unityskill.workspace.dto;

import com.unityskill.workspace.entity.Workspace;

public record WorkspaceResponse(
        String id,
        String name,
        String description,
        String slug,
        String createdBy,
        String createdAt,
        String updatedAt
) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(
                w.getId().toString(),
                w.getName(),
                w.getDescription(),
                w.getSlug(),
                w.getCreatedBy().toString(),
                w.getCreatedAt().toString(),
                w.getUpdatedAt().toString()
        );
    }
}
