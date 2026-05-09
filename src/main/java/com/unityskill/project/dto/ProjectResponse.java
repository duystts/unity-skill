package com.unityskill.project.dto;

import com.unityskill.project.entity.Project;

public record ProjectResponse(
    String id,
    String workspaceId,
    String name,
    String description,
    String visibility,
    String createdAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
            p.getId().toString(),
            p.getWorkspaceId().toString(),
            p.getName(),
            p.getDescription(),
            p.getVisibility().name(),
            p.getCreatedAt().toString()
        );
    }
}
