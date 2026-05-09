package com.unityskill.teamhealth;

import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.BlockedDecisionsResponse;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkloadAnalyzer {

    /** AC1(a): OPEN_POOL ticket unassigned for longer than this is a blocked decision. */
    static final long OPEN_POOL_THRESHOLD_HOURS = 24;

    /** AC1(b): ticket with a PR URL and no state change for longer than this is a blocked decision. */
    static final long PR_REVIEW_THRESHOLD_HOURS = 48;

    /**
     * AC1(c): ticket in a stage with no state change beyond this threshold.
     * Reuses InactivityAlertService.INACTIVITY_THRESHOLD_DAYS = 3.
     */
    static final long STALE_STAGE_THRESHOLD_DAYS = InactivityAlertService.INACTIVITY_THRESHOLD_DAYS;

    private final WorkspaceMemberRepository memberRepository;
    private final TicketRepository ticketRepository;

    /**
     * AC1, AC3, AC4: Returns all currently blocked tickets requiring PM attention.
     * Role check: DEVELOPER → UnauthorizedAccessException → 403.
     * Each ticket matches at most ONE reason (priority: UNASSIGNED_OPEN_POOL → PENDING_PR_REVIEW → STALE_STAGE).
     */
    public BlockedDecisionsResponse computeBlockedDecisions(UUID workspaceId, UUID callerId) {
        WorkspaceMember caller = memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (caller.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can view blocked decisions");
        }
        return computeBlockedDecisions(workspaceId);
    }

    /**
     * AC4: Internal workspace-scoped computation — only queries tickets for {@code workspaceId}.
     * Called by the public method above; also directly callable in tests.
     */
    public BlockedDecisionsResponse computeBlockedDecisions(UUID workspaceId) {
        Instant now               = Instant.now();
        Instant openPoolCutoff   = now.minus(OPEN_POOL_THRESHOLD_HOURS, ChronoUnit.HOURS);
        Instant prReviewCutoff   = now.minus(PR_REVIEW_THRESHOLD_HOURS, ChronoUnit.HOURS);
        Instant staleStageCutoff = now.minus(STALE_STAGE_THRESHOLD_DAYS * 24L, ChronoUnit.HOURS);

        // Reuses Story 8.1 repository method — workspace-scoped, never cross-workspace (AC4)
        List<Ticket> openTickets = ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId);

        List<BlockedDecisionsResponse.BlockedTicketInfo> blocked = new ArrayList<>();

        for (Ticket ticket : openTickets) {
            BlockedReason reason = null;

            // Priority 1 – UNASSIGNED_OPEN_POOL (AC1a)
            if (ticket.getAssignmentMode() == AssignmentMode.OPEN_POOL
                    && ticket.getAssigneeId() == null
                    && ticket.getUpdatedAt().isBefore(openPoolCutoff)) {
                reason = BlockedReason.UNASSIGNED_OPEN_POOL;

            // Priority 2 – PENDING_PR_REVIEW (AC1b)
            } else if (ticket.getGithubPrUrl() != null
                    && ticket.getUpdatedAt().isBefore(prReviewCutoff)) {
                reason = BlockedReason.PENDING_PR_REVIEW;

            // Priority 3 – STALE_STAGE (AC1c)
            } else if (ticket.getStageId() != null
                    && ticket.getUpdatedAt().isBefore(staleStageCutoff)) {
                reason = BlockedReason.STALE_STAGE;
            }

            if (reason != null) {
                long durationHours = ChronoUnit.HOURS.between(ticket.getUpdatedAt(), now);
                blocked.add(new BlockedDecisionsResponse.BlockedTicketInfo(
                        ticket.getId().toString(),
                        ticket.getTitle(),
                        ticket.getProjectId().toString(),
                        reason,
                        durationHours
                ));
            }
        }

        return new BlockedDecisionsResponse(blocked);
    }
}
