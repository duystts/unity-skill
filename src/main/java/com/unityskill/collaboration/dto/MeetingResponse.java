package com.unityskill.collaboration.dto;

import com.unityskill.collaboration.entity.Meeting;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class MeetingResponse {

    UUID id;
    UUID workspaceId;
    UUID projectId;
    String title;
    Instant scheduledAt;
    String status;       // MeetingStatus name — String for forward compatibility
    String agenda;       // null until Story 5.3
    String agendaStatus; // null until Story 5.3
    String summary;      // null until Story 5.5
    String actionItems;  // null until Story 5.5 — JSON array string
    String transcriptStatus; // null if no transcript uploaded — Story 5.5
    UUID createdBy;
    Instant createdAt;
    Instant updatedAt;

    // Existing factory — backward compatible for create/list/update/agendaGen (transcriptStatus = null)
    public static MeetingResponse from(Meeting m) {
        return from(m, null);
    }

    // Overloaded factory — used by getMeeting() which resolves transcript status
    public static MeetingResponse from(Meeting m, String transcriptStatus) {
        return MeetingResponse.builder()
                .id(m.getId())
                .workspaceId(m.getWorkspaceId())
                .projectId(m.getProjectId())
                .title(m.getTitle())
                .scheduledAt(m.getScheduledAt())
                .status(m.getStatus().name())
                .agenda(m.getAgenda())
                .agendaStatus(m.getAgendaStatus() != null ? m.getAgendaStatus().name() : null)
                .summary(m.getSummary())
                .actionItems(m.getActionItems())
                .transcriptStatus(transcriptStatus)
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
