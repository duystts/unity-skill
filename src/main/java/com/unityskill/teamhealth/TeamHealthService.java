package com.unityskill.teamhealth;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import com.unityskill.teamhealth.dto.TeamHealthResponse;
import com.unityskill.teamhealth.dto.WorkloadResponse;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.WorkspaceRepository;
import com.unityskill.workspace.entity.Workspace;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamHealthService {

    private final WorkspaceMemberRepository memberRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;  // Story 8.3: read thresholds

    /**
     * AC1: Compute on-demand team health summary for a workspace.
     * AC3: Only PM or Admin may call this; DEVELOPER role → 403.
     */
    public TeamHealthResponse getTeamHealth(UUID workspaceId, UUID callerId) {
        // AC3: only PM/Admin
        WorkspaceMember caller = memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (caller.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can view team health");
        }

        // Fetch all workspace members
        List<WorkspaceMember> members = memberRepository.findAllByWorkspaceId(workspaceId);
        List<UUID> memberUserIds = members.stream().map(WorkspaceMember::getUserId).toList();

        // Fetch display names
        Map<UUID, String> displayNameByUserId = userRepository.findAllById(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        // Fetch all open tickets in workspace
        List<Ticket> openTickets = ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId);

        // Overdue: open tickets where updatedAt < now() - 48h
        Instant overdueThreshold = Instant.now().minus(48, ChronoUnit.HOURS);
        int overdueCount = (int) openTickets.stream()
                .filter(t -> t.getUpdatedAt().isBefore(overdueThreshold))
                .count();

        // Group open tickets by assigneeId
        Map<UUID, List<Ticket>> ticketsByAssignee = openTickets.stream()
                .filter(t -> t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Ticket::getAssigneeId));

        // Build per-member health info
        List<TeamHealthResponse.MemberHealthInfo> memberInfos = members.stream()
                .map(member -> {
                    List<Ticket> memberTickets = ticketsByAssignee
                            .getOrDefault(member.getUserId(), List.of());

                    int openCount = memberTickets.size();

                    // Last activity = most recent updatedAt among their open tickets
                    String lastActivityDate = memberTickets.stream()
                            .map(Ticket::getUpdatedAt)
                            .max(Comparator.naturalOrder())
                            .map(Instant::toString)
                            .orElse(null);

                    WorkloadStatus status = WorkloadStatus.fromOpenTicketCount(openCount);
                    String displayName = displayNameByUserId.getOrDefault(
                            member.getUserId(), "Unknown");

                    return new TeamHealthResponse.MemberHealthInfo(
                            member.getUserId().toString(),
                            displayName,
                            member.getRole().name(),
                            openCount,
                            lastActivityDate,
                            status
                    );
                })
                .sorted(Comparator.comparingInt(TeamHealthResponse.MemberHealthInfo::openTicketCount)
                        .reversed())   // Story 8.3 AC: sorted by openTicketCount desc
                .toList();

        return new TeamHealthResponse(openTickets.size(), overdueCount, memberInfos);
    }

    /**
     * Story 8.3 AC1, AC4: Workload view — per-member ticket counts with PM-configurable thresholds.
     * Only PM/Admin may call this; DEVELOPER role → 403.
     */
    public WorkloadResponse getWorkload(UUID workspaceId, UUID callerId) {
        WorkspaceMember caller = memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (caller.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can view workload");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        int overloadedThreshold  = workspace.getOverloadedThreshold();
        int balancedMinThreshold = workspace.getBalancedMinThreshold();

        List<WorkspaceMember> members = memberRepository.findAllByWorkspaceId(workspaceId);
        List<UUID> memberUserIds = members.stream().map(WorkspaceMember::getUserId).toList();
        Map<UUID, String> displayNameByUserId = userRepository.findAllById(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        // Single workspace-scoped query (reuses Story 8.1 method)
        List<Ticket> openTickets = ticketRepository.findAllByWorkspaceIdAndClosedAtIsNull(workspaceId);
        Map<UUID, List<Ticket>> ticketsByAssignee = openTickets.stream()
                .filter(t -> t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Ticket::getAssigneeId));

        List<WorkloadResponse.MemberWorkloadInfo> memberInfos = members.stream()
                .map(member -> {
                    List<Ticket> memberTickets = ticketsByAssignee
                            .getOrDefault(member.getUserId(), List.of());
                    int openCount = memberTickets.size();
                    int inProgressCount = (int) memberTickets.stream()
                            .filter(t -> t.getStageId() != null)
                            .count();
                    WorkloadStatus status = WorkloadStatus.fromOpenTicketCount(
                            openCount, balancedMinThreshold, overloadedThreshold);
                    String displayName = displayNameByUserId.getOrDefault(
                            member.getUserId(), "Unknown");
                    return new WorkloadResponse.MemberWorkloadInfo(
                            member.getUserId().toString(), displayName, member.getRole().name(),
                            openCount, inProgressCount, status);
                })
                .sorted(Comparator.comparingInt(WorkloadResponse.MemberWorkloadInfo::openTicketCount)
                        .reversed())  // AC4
                .toList();

        return new WorkloadResponse(memberInfos);
    }

    /**
     * Story 8.3 AC3: Returns a specific member's open tickets for the workload member-detail page.
     * Only PM/Admin may call this.
     */
    public List<Ticket> getMemberOpenTickets(UUID workspaceId, UUID callerId, UUID targetUserId) {
        WorkspaceMember caller = memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (caller.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can view member workload");
        }
        return ticketRepository.findAllByWorkspaceIdAndAssigneeIdAndClosedAtIsNull(workspaceId, targetUserId);
    }
}
