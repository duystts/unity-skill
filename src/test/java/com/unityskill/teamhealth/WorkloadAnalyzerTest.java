package com.unityskill.teamhealth;

import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.BlockedDecisionsResponse;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkloadAnalyzerTest {

    @Mock WorkspaceMemberRepository memberRepository;
    @Mock TicketRepository ticketRepository;
    @InjectMocks WorkloadAnalyzer workloadAnalyzer;

    private static final UUID WS_ID  = UUID.randomUUID();
    private static final UUID PM_ID  = UUID.randomUUID();
    private static final UUID DEV_ID = UUID.randomUUID();

    private WorkspaceMember member(UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(WS_ID)
                .userId(userId).role(role).build();
    }

    private Ticket openPoolTicket(Instant updatedAt) {
        return Ticket.builder()
                .id(UUID.randomUUID()).workspaceId(WS_ID).projectId(UUID.randomUUID())
                .title("Unassigned Task").assignmentMode(AssignmentMode.OPEN_POOL)
                .assigneeId(null).stageId(null).githubPrUrl(null)
                .updatedAt(updatedAt).build();
    }

    private Ticket prTicket(Instant updatedAt) {
        return Ticket.builder()
                .id(UUID.randomUUID()).workspaceId(WS_ID).projectId(UUID.randomUUID())
                .title("PR Task").assignmentMode(AssignmentMode.ASSIGNED)
                .assigneeId(DEV_ID).stageId(UUID.randomUUID())
                .githubPrUrl("https://github.com/org/repo/pull/42")
                .updatedAt(updatedAt).build();
    }

    private Ticket staleStageTicket(Instant updatedAt) {
        return Ticket.builder()
                .id(UUID.randomUUID()).workspaceId(WS_ID).projectId(UUID.randomUUID())
                .title("Stale Task").assignmentMode(AssignmentMode.ASSIGNED)
                .assigneeId(DEV_ID).stageId(UUID.randomUUID())
                .githubPrUrl(null).updatedAt(updatedAt).build();
    }

    @Test
    void computeBlockedDecisions_openPoolUnassignedBeyond24h_returnsUnassignedOpenPool() {
        // AC1(a)
        when(memberRepository.findByWorkspaceIdAndUserId(WS_ID, PM_ID))
                .thenReturn(Optional.of(member(PM_ID, WorkspaceRole.PM)));
        Instant staleTime = Instant.now().minus(25, ChronoUnit.HOURS);
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(WS_ID))
                .thenReturn(List.of(openPoolTicket(staleTime)));

        BlockedDecisionsResponse result = workloadAnalyzer.computeBlockedDecisions(WS_ID, PM_ID);

        assertThat(result.tickets()).hasSize(1);
        assertThat(result.tickets().get(0).blockedReason()).isEqualTo(BlockedReason.UNASSIGNED_OPEN_POOL);
        assertThat(result.tickets().get(0).blockedDurationHours()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void computeBlockedDecisions_prPendingBeyond48h_returnsPendingPrReview() {
        // AC1(b)
        when(memberRepository.findByWorkspaceIdAndUserId(WS_ID, PM_ID))
                .thenReturn(Optional.of(member(PM_ID, WorkspaceRole.PM)));
        Instant staleTime = Instant.now().minus(49, ChronoUnit.HOURS);
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(WS_ID))
                .thenReturn(List.of(prTicket(staleTime)));

        BlockedDecisionsResponse result = workloadAnalyzer.computeBlockedDecisions(WS_ID, PM_ID);

        assertThat(result.tickets()).hasSize(1);
        assertThat(result.tickets().get(0).blockedReason()).isEqualTo(BlockedReason.PENDING_PR_REVIEW);
        assertThat(result.tickets().get(0).blockedDurationHours()).isGreaterThanOrEqualTo(49);
    }

    @Test
    void computeBlockedDecisions_staleStageBeyond3Days_returnsStaleStage() {
        // AC1(c)
        when(memberRepository.findByWorkspaceIdAndUserId(WS_ID, PM_ID))
                .thenReturn(Optional.of(member(PM_ID, WorkspaceRole.PM)));
        Instant staleTime = Instant.now().minus(4, ChronoUnit.DAYS);
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(WS_ID))
                .thenReturn(List.of(staleStageTicket(staleTime)));

        BlockedDecisionsResponse result = workloadAnalyzer.computeBlockedDecisions(WS_ID, PM_ID);

        assertThat(result.tickets()).hasSize(1);
        assertThat(result.tickets().get(0).blockedReason()).isEqualTo(BlockedReason.STALE_STAGE);
        assertThat(result.tickets().get(0).blockedDurationHours()).isGreaterThanOrEqualTo(96);
    }

    @Test
    void computeBlockedDecisions_recentlyUpdatedTickets_notReturned() {
        // AC3: ticket whose blocking was resolved — recent updatedAt → excluded
        when(memberRepository.findByWorkspaceIdAndUserId(WS_ID, PM_ID))
                .thenReturn(Optional.of(member(PM_ID, WorkspaceRole.PM)));
        // PR ticket updated 1 hour ago — well within 48h threshold → NOT blocked
        Instant recent = Instant.now().minus(1, ChronoUnit.HOURS);
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(WS_ID))
                .thenReturn(List.of(prTicket(recent)));

        BlockedDecisionsResponse result = workloadAnalyzer.computeBlockedDecisions(WS_ID, PM_ID);

        assertThat(result.tickets()).isEmpty();
    }

    @Test
    void computeBlockedDecisions_developerCaller_throwsUnauthorized() {
        // AC1 role check
        when(memberRepository.findByWorkspaceIdAndUserId(WS_ID, DEV_ID))
                .thenReturn(Optional.of(member(DEV_ID, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() -> workloadAnalyzer.computeBlockedDecisions(WS_ID, DEV_ID))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Only PM or Admin");
    }
}
