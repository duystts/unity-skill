package com.unityskill.project.dto;

import com.unityskill.project.entity.Ticket;

public record TicketResponse(
    String id,
    String workspaceId,
    String projectId,
    String stageId,
    String ticketCode,   // e.g. "US-3" — shown in UI and used to name PRs
    String title,
    String description,
    String assigneeId,
    String assignmentMode,
    String githubPrUrl,
    boolean hasPr,       // convenience flag: true when githubPrUrl is non-null
    String closedAt,
    String createdAt,
    String updatedAt
) {
    /**
     * @param keyPrefix the project's key prefix (e.g. "US"), used to form the ticket code
     */
    public static TicketResponse from(Ticket t, String keyPrefix) {
        String code = keyPrefix + "-" + t.getTicketNumber();
        return new TicketResponse(
            t.getId().toString(),
            t.getWorkspaceId().toString(),
            t.getProjectId().toString(),
            t.getStageId() != null ? t.getStageId().toString() : null,
            code,
            t.getTitle(),
            t.getDescription(),
            t.getAssigneeId() != null ? t.getAssigneeId().toString() : null,
            t.getAssignmentMode().name(),
            t.getGithubPrUrl(),
            t.getGithubPrUrl() != null,
            t.getClosedAt() != null ? t.getClosedAt().toString() : null,
            t.getCreatedAt().toString(),
            t.getUpdatedAt().toString()
        );
    }
}
