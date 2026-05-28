package com.unityskill.project;

import com.unityskill.auth.UserRepository;
import com.unityskill.project.dto.TicketActivityResponse;
import com.unityskill.project.entity.ActivityType;
import com.unityskill.project.entity.Ticket;
import com.unityskill.project.entity.TicketActivity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketActivityService {

    private final TicketActivityRepository activityRepository;
    private final TicketRepository ticketRepository;
    private final WorkflowStageRepository stageRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    // ── Write helpers ────────────────────────────────────────────────────────

    @Transactional
    public void logCreated(Ticket ticket, UUID actorId, String actorName) {
        activityRepository.save(TicketActivity.builder()
            .ticketId(ticket.getId())
            .projectId(ticket.getProjectId())
            .workspaceId(ticket.getWorkspaceId())
            .actorId(actorId)
            .actorName(actorName)
            .type(ActivityType.TICKET_CREATED)
            .toStageId(ticket.getStageId())
            .build());
    }

    @Transactional
    public void logStageChanged(Ticket ticket, UUID fromStageId, UUID toStageId, UUID actorId, String actorName) {
        activityRepository.save(TicketActivity.builder()
            .ticketId(ticket.getId())
            .projectId(ticket.getProjectId())
            .workspaceId(ticket.getWorkspaceId())
            .actorId(actorId)
            .actorName(actorName)
            .type(ActivityType.STAGE_CHANGED)
            .fromStageId(fromStageId)
            .toStageId(toStageId)
            .build());
    }

    @Transactional
    public void logPrLinked(Ticket ticket, UUID actorId, String actorName) {
        activityRepository.save(TicketActivity.builder()
            .ticketId(ticket.getId())
            .projectId(ticket.getProjectId())
            .workspaceId(ticket.getWorkspaceId())
            .actorId(actorId)
            .actorName(actorName)
            .type(ActivityType.PR_LINKED)
            .build());
    }

    // ── Read ────────────────────────────────────────────────────────────────

    public List<TicketActivityResponse> listProjectActivities(UUID workspaceId, UUID projectId) {
        List<TicketActivity> activities =
            activityRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId);

        String keyPrefix = projectRepository.findById(projectId)
            .map(p -> p.getKeyPrefix()).orElse("TICKET");

        Map<UUID, String> stageNames = stageRepository
            .findAllByProjectIdOrderByPositionAsc(projectId).stream()
            .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));

        Map<UUID, String> ticketCodes = ticketRepository.findAllByProjectId(projectId).stream()
            .collect(Collectors.toMap(
                t -> t.getId(),
                t -> keyPrefix + "-" + t.getTicketNumber()
            ));

        Map<UUID, String> ticketTitles = ticketRepository.findAllByProjectId(projectId).stream()
            .collect(Collectors.toMap(t -> t.getId(), t -> t.getTitle()));

        return activities.stream()
            .map(a -> TicketActivityResponse.from(
                a,
                ticketCodes.getOrDefault(a.getTicketId(), "?"),
                ticketTitles.getOrDefault(a.getTicketId(), ""),
                a.getFromStageId() != null ? stageNames.getOrDefault(a.getFromStageId(), "?") : null,
                a.getToStageId()   != null ? stageNames.getOrDefault(a.getToStageId(),   "?") : null
            ))
            .toList();
    }

    // ── Util ────────────────────────────────────────────────────────────────

    /** Resolve display name from User entity (email + displayName live on User, not WorkspaceMember). */
    public String resolveActorName(UUID workspaceId, UUID userId) {
        return userRepository.findById(userId)
            .map(u -> {
                if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) return u.getDisplayName();
                if (u.getEmail()       != null && !u.getEmail().isBlank())       return u.getEmail();
                return userId.toString().substring(0, 8);
            })
            .orElse("Unknown");
    }
}
