package com.unityskill.teamhealth;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.WorkloadResponse;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.WorkspaceRepository;
import com.unityskill.workspace.entity.Workspace;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

    @Mock WorkspaceMemberRepository memberRepository;
    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @InjectMocks TeamHealthService teamHealthService;

    private Workspace workspace(UUID id, int overloaded, int balancedMin) {
        return Workspace.builder()
                .id(id).name("WS").createdBy(UUID.randomUUID()).slug("ws")
                .overloadedThreshold(overloaded).balancedMinThreshold(balancedMin)
                .build();
    }

    @Test
    void getWorkload_pmCaller_returnsWorkloadWithCorrectCounts() {
        // AC1: PM caller gets per-member workload with inProgressTicketCount
        UUID workspaceId = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();

        WorkspaceMember pm  = WorkspaceMember.builder().id(UUID.randomUUID())
                .workspaceId(workspaceId).userId(pmId).role(WorkspaceRole.PM).build();
        WorkspaceMember dev = WorkspaceMember.builder().id(UUID.randomUUID())
                .workspaceId(workspaceId).userId(devId).role(WorkspaceRole.DEVELOPER).build();
        User pmUser  = User.builder().id(pmId).email("pm@e.com").displayName("PM").build();
        User devUser = User.builder().id(devId).email("dev@e.com").displayName("Dev").build();

        // dev: 2 open tickets — 1 with stage (inProgress), 1 without
        Ticket t1 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).title("T1").stageId(UUID.randomUUID())
                .assignmentMode(AssignmentMode.ASSIGNED)
                .updatedAt(Instant.now()).build();
        Ticket t2 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).title("T2").stageId(null)  // no stage
                .assignmentMode(AssignmentMode.ASSIGNED)
                .updatedAt(Instant.now()).build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId)).thenReturn(Optional.of(pm));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace(workspaceId, 5, 2)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(pm, dev));
        when(userRepository.findAllById(List.of(pmId, devId))).thenReturn(List.of(pmUser, devUser));
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId))
                .thenReturn(List.of(t1, t2));

        WorkloadResponse result = teamHealthService.getWorkload(workspaceId, pmId);

        assertThat(result.members()).hasSize(2);
        // AC4: sorted by openTicketCount desc → dev (2 tickets) first
        WorkloadResponse.MemberWorkloadInfo devInfo = result.members().get(0);
        assertThat(devInfo.userId()).isEqualTo(devId.toString());
        assertThat(devInfo.openTicketCount()).isEqualTo(2);
        assertThat(devInfo.inProgressTicketCount()).isEqualTo(1);  // only t1 has stageId
        assertThat(devInfo.workloadStatus()).isEqualTo(WorkloadStatus.BALANCED);

        WorkloadResponse.MemberWorkloadInfo pmInfo = result.members().get(1);
        assertThat(pmInfo.openTicketCount()).isEqualTo(0);
        assertThat(pmInfo.workloadStatus()).isEqualTo(WorkloadStatus.AVAILABLE);
    }

    @Test
    void getWorkload_customThresholds_overridesDefaultClassification() {
        // AC2: workspace has custom thresholds (overloaded=1) — 2 tickets → OVERLOADED
        UUID workspaceId = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();

        WorkspaceMember pm  = WorkspaceMember.builder().id(UUID.randomUUID())
                .workspaceId(workspaceId).userId(pmId).role(WorkspaceRole.PM).build();
        WorkspaceMember dev = WorkspaceMember.builder().id(UUID.randomUUID())
                .workspaceId(workspaceId).userId(devId).role(WorkspaceRole.DEVELOPER).build();
        User pmUser  = User.builder().id(pmId).email("pm@e.com").displayName("PM").build();
        User devUser = User.builder().id(devId).email("dev@e.com").displayName("Dev").build();

        Ticket t1 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).title("T1").stageId(UUID.randomUUID())
                .assignmentMode(AssignmentMode.ASSIGNED).updatedAt(Instant.now()).build();
        Ticket t2 = Ticket.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).title("T2").stageId(UUID.randomUUID())
                .assignmentMode(AssignmentMode.ASSIGNED).updatedAt(Instant.now()).build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId)).thenReturn(Optional.of(pm));
        // Custom: overloaded > 1, balanced >= 1 → dev with 2 tickets → OVERLOADED
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace(workspaceId, 1, 1)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(pm, dev));
        when(userRepository.findAllById(List.of(pmId, devId))).thenReturn(List.of(pmUser, devUser));
        when(ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId))
                .thenReturn(List.of(t1, t2));

        WorkloadResponse result = teamHealthService.getWorkload(workspaceId, pmId);

        WorkloadResponse.MemberWorkloadInfo devInfo = result.members().get(0);
        assertThat(devInfo.workloadStatus()).isEqualTo(WorkloadStatus.OVERLOADED);
    }

    @Test
    void getWorkload_developerCaller_throwsUnauthorized() {
        // AC3 (role check): DEVELOPER → 403
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();
        WorkspaceMember dev = WorkspaceMember.builder().id(UUID.randomUUID())
                .workspaceId(workspaceId).userId(devId).role(WorkspaceRole.DEVELOPER).build();
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, devId)).thenReturn(Optional.of(dev));

        assertThatThrownBy(() -> teamHealthService.getWorkload(workspaceId, devId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Only PM or Admin");
    }
}
