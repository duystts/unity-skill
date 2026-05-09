package com.unityskill.teamhealth.dto;

import com.unityskill.teamhealth.BlockedReason;

import java.util.List;

public record BlockedDecisionsResponse(List<BlockedTicketInfo> tickets) {

    /**
     * AC2: ticket title, blocked reason, blocked duration hours, and projectId for frontend link.
     * Frontend constructs: /{workspaceId}/projects/{projectId}/tickets/{ticketId}
     */
    public record BlockedTicketInfo(
            String ticketId,
            String title,
            String projectId,           // needed for the frontend deep link
            BlockedReason blockedReason,
            long blockedDurationHours   // positive; computed as hours since ticket.updatedAt
    ) {}
}
