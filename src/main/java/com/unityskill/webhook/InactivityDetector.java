package com.unityskill.webhook;

import com.unityskill.notification.NotificationService;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InactivityDetector {

    static final long INACTIVITY_THRESHOLD_HOURS = 48;
    static final long ALERT_COOLDOWN_HOURS = 24;

    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 */6 * * *")
    public void detectAndAlert() {
        Instant inactivityCutoff = Instant.now().minus(INACTIVITY_THRESHOLD_HOURS, ChronoUnit.HOURS);
        Instant alertCooldown = Instant.now().minus(ALERT_COOLDOWN_HOURS, ChronoUnit.HOURS);

        // AC1: find all non-closed tickets inactive for 48+ hours
        List<Ticket> candidates = ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(inactivityCutoff);

        for (Ticket ticket : candidates) {
            // AC3: skip if already alerted within the last 24 hours
            if (ticket.getLastAlertedAt() != null
                    && ticket.getLastAlertedAt().isAfter(alertCooldown)) {
                continue;
            }

            long stuckHours = ChronoUnit.HOURS.between(ticket.getUpdatedAt(), Instant.now());
            UUID workspaceId = ticket.getWorkspaceId();

            // AC2: find all PM and Admin members in the workspace
            List<UUID> pmUserIds = memberRepository
                    .findAllByWorkspaceIdAndRoleIn(workspaceId,
                            List.of(WorkspaceRole.PM, WorkspaceRole.ADMIN))
                    .stream()
                    .map(m -> m.getUserId())
                    .toList();

            // AC2: send TICKET_INACTIVE notification to each PM/Admin
            Map<String, Object> payload = Map.of(
                    "ticketId", ticket.getId().toString(),
                    "title", ticket.getTitle(),
                    "stuckForHours", stuckHours
            );

            for (UUID pmUserId : pmUserIds) {
                notificationService.notify(pmUserId, workspaceId, "TICKET_INACTIVE", payload);
            }

            // AC3: update last_alerted_at WITHOUT bumping updated_at
            ticketRepository.updateLastAlertedAt(ticket.getId(), Instant.now());
        }
    }
}
