package com.unityskill.project.dto;

import com.unityskill.project.entity.TicketActivity;

import java.time.Instant;
import java.util.UUID;

public record TicketActivityResponse(
    UUID id,
    UUID ticketId,
    String ticketCode,       // e.g. "D-1"
    String ticketTitle,
    UUID actorId,
    String actorName,
    String type,             // TICKET_CREATED | STAGE_CHANGED | PR_LINKED | TICKET_ASSIGNED
    UUID fromStageId,
    String fromStageName,
    UUID toStageId,
    String toStageName,
    Instant createdAt
) {
    public static TicketActivityResponse from(
            TicketActivity a,
            String ticketCode,
            String ticketTitle,
            String fromStageName,
            String toStageName) {
        return new TicketActivityResponse(
            a.getId(),
            a.getTicketId(),
            ticketCode,
            ticketTitle,
            a.getActorId(),
            a.getActorName(),
            a.getType().name(),
            a.getFromStageId(),
            fromStageName,
            a.getToStageId(),
            toStageName,
            a.getCreatedAt()
        );
    }
}
