package com.unityskill.teamhealth;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.TeamHealthResponse;
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
class TeamHealthServiceTest {

    @Mock WorkspaceMemberRepository memberRepository;
    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @InjectMocks TeamHealthService teamHealthService;

    @Test
    void getTeamHealth_pmCaller_returnsAggregatedHealthData() {
        // AC1: PM caller gets full health summary
        UUID workspaceId = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();

        WorkspaceMember pm = WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).userId(pmId)
                .role(WorkspaceRole.PM).build();
        WorkspaceMember dev = WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).userId(devId)
                .role(WorkspaceRole.DEVELOPER).build();

        User pmUser  = User.builder().id(pmId).email("pm@e.com").displayName("PM User").build();
        User devUser = User.builder().id(devId).email("dev@e.com").displayName("Dev User").build();

        // dev has 2 open tickets (BALANCED)
        Ticket t1 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).assignmentMode(AssignmentMode.ASSIGNED)
                .title("T1").updatedAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        Ticket t2 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).assignmentMode(AssignmentMode.ASSIGNED)
                .title("T2").updatedAt(Instant.now().minus(10, ChronoUnit.HOURS)).build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId))
                .thenReturn(Optional.of(pm));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(pm, dev));
        when(userRepository.findAllById(List.of(pmId, devId))).thenReturn(List.of(pmUser, devUser));
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId))
                .thenReturn(List.of(t1, t2));

        TeamHealthResponse result = teamHealthService.getTeamHealth(workspaceId, pmId);

        assertThat(result.totalOpenTickets()).isEqualTo(2);
        assertThat(result.overdueTickets()).isEqualTo(0); // both updated recently
        assertThat(result.members()).hasSize(2);

        // dev has 2 tickets → BALANCED; should be first (sorted by openTicketCount desc)
        TeamHealthResponse.MemberHealthInfo devInfo = result.members().get(0);
        assertThat(devInfo.userId()).isEqualTo(devId.toString());
        assertThat(devInfo.openTicketCount()).isEqualTo(2);
        assertThat(devInfo.workloadStatus()).isEqualTo(WorkloadStatus.BALANCED);
        assertThat(devInfo.lastActivityDate()).isNotNull();

        // PM has 0 tickets → AVAILABLE
        TeamHealthResponse.MemberHealthInfo pmInfo = result.members().get(1);
        assertThat(pmInfo.userId()).isEqualTo(pmId.toString());
        assertThat(pmInfo.openTicketCount()).isEqualTo(0);
        assertThat(pmInfo.workloadStatus()).isEqualTo(WorkloadStatus.AVAILABLE);
        assertThat(pmInfo.lastActivityDate()).isNull();
    }

    @Test
    void getTeamHealth_overdueTicketsAreCountedCorrectly() {
        // AC1: tickets with updatedAt > 48h ago are overdue
        UUID workspaceId = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();

        WorkspaceMember pm = WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).userId(pmId)
                .role(WorkspaceRole.PM).build();
        User pmUser = User.builder().id(pmId).email("pm@e.com").displayName("PM").build();

        // One recent, two overdue
        Ticket recent = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .title("Recent").updatedAt(Instant.now().minus(10, ChronoUnit.HOURS)).build();
        Ticket overdue1 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .title("Overdue1").updatedAt(Instant.now().minus(72, ChronoUnit.HOURS)).build();
        Ticket overdue2 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .title("Overdue2").updatedAt(Instant.now().minus(50, ChronoUnit.HOURS)).build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId))
                .thenReturn(Optional.of(pm));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(pm));
        when(userRepository.findAllById(List.of(pmId))).thenReturn(List.of(pmUser));
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId))
                .thenReturn(List.of(recent, overdue1, overdue2));

        TeamHealthResponse result = teamHealthService.getTeamHealth(workspaceId, pmId);

        assertThat(result.totalOpenTickets()).isEqualTo(3);
        assertThat(result.overdueTickets()).isEqualTo(2);
    }

    @Test
    void getTeamHealth_developerCaller_throwsUnauthorized() {
        // AC3: DEVELOPER role → 403
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();

        WorkspaceMember dev = WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId).userId(devId)
                .role(WorkspaceRole.DEVELOPER).build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, devId))
                .thenReturn(Optional.of(dev));

        assertThatThrownBy(() -> teamHealthService.getTeamHealth(workspaceId, devId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Only PM or Admin");
    }
}
