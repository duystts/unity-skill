package com.unityskill.teamhealth;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.collaboration.ChatRepository;
import com.unityskill.contribution.ContributionRepository;
import com.unityskill.notification.NotificationService;
import com.unityskill.project.TicketRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.WorkspaceRepository;
import com.unityskill.workspace.entity.Workspace;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InactivityAlertService {

    /** AC1: members with no activity for more than this many days are considered inactive. */
    static final long INACTIVITY_THRESHOLD_DAYS = 3;

    /** AC3: do not send a second MEMBER_INACTIVE alert within this window. */
    static final long ALERT_COOLDOWN_HOURS = 24;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final ChatRepository chatRepository;
    private final ContributionRepository contributionRepository;
    private final NotificationService notificationService;

    /**
     * AC1: Daily evaluation of member activity across all workspaces.
     * Runs at 02:00 UTC daily (offset from StreakService at 01:00 to avoid contention).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void detectAndAlertMemberInactivity() {
        Instant activityCutoff = Instant.now().minus(INACTIVITY_THRESHOLD_DAYS * 24L, ChronoUnit.HOURS);
        Instant alertCooldown  = Instant.now().minus(ALERT_COOLDOWN_HOURS, ChronoUnit.HOURS);

        log.info("Running member inactivity check (threshold: {} days)", INACTIVITY_THRESHOLD_DAYS);

        List<Workspace> workspaces = workspaceRepository.findAll();

        for (Workspace workspace : workspaces) {
            UUID workspaceId = workspace.getId();
            List<WorkspaceMember> members = memberRepository.findAllByWorkspaceId(workspaceId);

            // AC1: derive active user IDs from ticket updates, chat messages, and PR events
            Set<UUID> activeUserIds = findActiveUserIds(workspaceId, activityCutoff);

            // PM/Admin list to notify — computed once per workspace
            List<UUID> pmIds = members.stream()
                    .filter(m -> m.getRole() != WorkspaceRole.DEVELOPER)
                    .map(WorkspaceMember::getUserId)
                    .toList();

            // Preload display names
            List<UUID> memberUserIds = members.stream().map(WorkspaceMember::getUserId).toList();
            Map<UUID, String> displayNames = userRepository.findAllById(memberUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getDisplayName));

            for (WorkspaceMember member : members) {
                UUID userId = member.getUserId();

                // PMs/Admins are the alerters, not the alertees — only monitor developers
                if (member.getRole() != WorkspaceRole.DEVELOPER) {
                    continue;
                }

                // AC4: skip if member was active
                if (activeUserIds.contains(userId)) {
                    continue;
                }

                // AC3: skip if already alerted within the cooldown window
                if (member.getLastInactivityAlertedAt() != null
                        && member.getLastInactivityAlertedAt().isAfter(alertCooldown)) {
                    log.debug("Skipping duplicate alert for user {} in workspace {}", userId, workspaceId);
                    continue;
                }

                // AC2: send silent MEMBER_INACTIVE notification to all PMs/Admins
                String memberName = displayNames.getOrDefault(userId, "Unknown");
                Map<String, Object> payload = Map.of(
                        "userId", userId.toString(),
                        "memberName", memberName,
                        "inactiveDays", INACTIVITY_THRESHOLD_DAYS + 1
                );

                for (UUID pmId : pmIds) {
                    notificationService.notify(pmId, workspaceId, "MEMBER_INACTIVE", payload);
                }

                // AC3: update last_inactivity_alerted_at WITHOUT touching updatedAt
                memberRepository.updateLastInactivityAlertedAt(member.getId(), Instant.now());
                log.debug("Sent MEMBER_INACTIVE alert for user {} in workspace {}", userId, workspaceId);
            }
        }

        log.info("Member inactivity check complete");
    }

    /**
     * AC1: Collect user IDs with any detectable activity since {@code since} in the given workspace.
     * Activity sources: ticket updates (assignee), chat messages (sender), contribution events (userId).
     */
    private Set<UUID> findActiveUserIds(UUID workspaceId, Instant since) {
        Set<UUID> active = new HashSet<>();

        // Ticket activity: tickets with non-null assignee updated after cutoff
        ticketRepository
                .findAllByWorkspaceIdAndAssigneeIdIsNotNullAndUpdatedAtAfter(workspaceId, since)
                .forEach(t -> active.add(t.getAssigneeId()));

        // Chat message activity: messages sent after cutoff (senderId may be null — ON DELETE SET NULL)
        chatRepository
                .findAllByWorkspaceIdAndCreatedAtAfter(workspaceId, since)
                .stream().filter(m -> m.getSenderId() != null)
                .forEach(m -> active.add(m.getSenderId()));

        // Contribution event activity: PR events, transcript analysis, chat contribution analysis
        contributionRepository
                .findAllByWorkspaceIdAndCreatedAtAfter(workspaceId, since)
                .forEach(e -> active.add(e.getUserId()));

        return active;
    }
}
