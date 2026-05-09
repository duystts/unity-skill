package com.unityskill.project.dto;

import com.unityskill.project.entity.Ticket;

public record TicketResponse(
    String id,
    String workspaceId,
    String projectId,
    String stageId,
    String title,
    String description,
    String assigneeId,
    String assignmentMode,
    String githubPrUrl,
    String closedAt,
    String createdAt,
    String updatedAt
) {
    public static TicketResponse from(Ticket t) {
        return new TicketResponse(
            t.getId().toString(),
            t.getWorkspaceId().toString(),
            t.getProjectId().toString(),
            t.getStageId() != null ? t.getStageId().toString() : null,
            t.getTitle(),
            t.getDescription(),
            t.getAssigneeId() != null ? t.getAssigneeId().toString() : null,
            t.getAssignmentMode().name(),
            t.getGithubPrUrl(),
            t.getClosedAt() != null ? t.getClosedAt().toString() : null,
            t.getCreatedAt().toString(),
            t.getUpdatedAt().toString()
        );
    }
}
