package com.unityskill.project;

import com.unityskill.achievement.AchievementEvaluator;
import com.unityskill.achievement.TicketTagRepository;
import com.unityskill.achievement.entity.TicketTag;
import com.unityskill.common.exception.InvalidAssigneeException;
import com.unityskill.common.exception.TicketAlreadyClaimedException;
import com.unityskill.common.exception.TicketNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.NotificationService;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.dto.CreateTicketRequest;
import com.unityskill.project.dto.TicketResponse;
import com.unityskill.project.dto.UpdateTicketRequest;
import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final TicketActivityService ticketActivityService;
    private final AchievementEvaluator achievementEvaluator;
    private final TicketTagRepository ticketTagRepository;

    /** Resolves the keyPrefix for a project, falling back to "PROJ" if not found. */
    private String keyPrefixFor(UUID projectId) {
        return projectRepository.findById(projectId)
            .map(p -> p.getKeyPrefix())
            .orElse("PROJ");
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest req, UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);

        // Assign next sequential ticket number within the project (atomic within this transaction)
        int nextNumber = ticketRepository.findMaxTicketNumberByProjectId(projectId) + 1;

        Ticket ticket = Ticket.builder()
            .workspaceId(workspaceId)
            .projectId(projectId)
            .stageId(req.stageId())
            .title(req.title())
            .description(req.description())
            .ticketNumber(nextNumber)
            .build();

        ticket = ticketRepository.saveAndFlush(ticket);
        String actorName = ticketActivityService.resolveActorName(workspaceId, callerId);
        ticketActivityService.logCreated(ticket, callerId, actorName);
        return TicketResponse.from(ticket, keyPrefixFor(projectId));
    }

    public List<TicketResponse> listTickets(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        String prefix = keyPrefixFor(projectId);
        List<Ticket> tickets = ticketRepository.findAllByProjectId(projectId);

        // Batch-fetch all tags for these tickets in one query — avoids N+1
        List<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toList());
        Map<UUID, List<String>> tagsByTicket = ticketTagRepository.findAllByTicketIdIn(ticketIds)
            .stream()
            .collect(Collectors.groupingBy(
                TicketTag::getTicketId,
                Collectors.mapping(tag -> tag.getTag().name(), Collectors.toList())
            ));

        return tickets.stream()
            .map(t -> TicketResponse.from(t, prefix, tagsByTicket.getOrDefault(t.getId(), List.of())))
            .toList();
    }

    public List<TicketResponse> listOpenPoolTickets(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        String prefix = keyPrefixFor(projectId);
        return ticketRepository.findAllByProjectIdAndAssignmentMode(projectId, AssignmentMode.OPEN_POOL)
            .stream().map(t -> TicketResponse.from(t, prefix)).toList();
    }

    public List<TicketResponse> listMyTickets(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        // Each ticket may belong to a different project — resolve prefix per ticket
        return ticketRepository.findAllByWorkspaceIdAndAssigneeId(workspaceId, callerId)
            .stream().map(t -> TicketResponse.from(t, keyPrefixFor(t.getProjectId()))).toList();
    }

    /**
     * Cross-workspace: returns all tickets assigned to the caller across every workspace.
     * Used by the skill profile page to show holistic contribution stats without
     * re-fetching per workspace.
     */
    public List<TicketResponse> listMyTicketsGlobal(UUID callerId) {
        List<Ticket> tickets = ticketRepository.findAllByAssigneeId(callerId);
        List<UUID> ids = tickets.stream().map(Ticket::getId).collect(Collectors.toList());
        Map<UUID, List<String>> tagsByTicket = ticketTagRepository.findAllByTicketIdIn(ids)
            .stream()
            .collect(Collectors.groupingBy(
                TicketTag::getTicketId,
                Collectors.mapping(tag -> tag.getTag().name(), Collectors.toList())
            ));
        return tickets.stream()
            .map(t -> TicketResponse.from(t, keyPrefixFor(t.getProjectId()),
                tagsByTicket.getOrDefault(t.getId(), List.of())))
            .toList();
    }

    @Transactional
    public TicketResponse updateTicket(UpdateTicketRequest req, UUID workspaceId, UUID projectId, UUID ticketId, UUID callerId) {
        requireMember(workspaceId, callerId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(TicketNotFoundException::new);

        if (!ticket.getProjectId().equals(projectId)) {
            throw new TicketNotFoundException();
        }

        if (req.title() != null) {
            ticket.setTitle(req.title());
        }
        if (req.description() != null) {
            ticket.setDescription(req.description());
        }
        if (req.githubPrUrl() != null) {
            ticket.setGithubPrUrl(req.githubPrUrl());
        }

        // closedAt logic — only when stageId changes
        UUID previousStageId = ticket.getStageId();
        if (req.stageId() != null && !req.stageId().equals(ticket.getStageId())) {
            ticket.setStageId(req.stageId());
            final Ticket ticketRef = ticket;
            stageRepository.findById(req.stageId()).ifPresent(stage -> {
                if (stage.isClosedState()) {
                    ticketRef.setClosedAt(Instant.now());
                } else {
                    ticketRef.setClosedAt(null);
                }
            });
        }

        // Assignment logic — PM/Admin only (Story 3.5)
        if (req.assigneeId() != null) {
            requirePmOrAdmin(workspaceId, callerId);
            if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, req.assigneeId())) {
                throw new InvalidAssigneeException();
            }
            ticket.setAssigneeId(req.assigneeId());
            ticket.setAssignmentMode(req.assignmentMode() != null ? req.assignmentMode() : AssignmentMode.ASSIGNED);
        }
        // Open Pool mode — PM/Admin only (Story 3.6)
        else if (req.assignmentMode() == AssignmentMode.OPEN_POOL) {
            requirePmOrAdmin(workspaceId, callerId);
            ticket.setAssigneeId(null);
            ticket.setAssignmentMode(AssignmentMode.OPEN_POOL);
        }

        ticket = ticketRepository.saveAndFlush(ticket);

        // Log stage change activity
        if (req.stageId() != null && !req.stageId().equals(previousStageId)) {
            String actorName = ticketActivityService.resolveActorName(workspaceId, callerId);
            ticketActivityService.logStageChanged(ticket, previousStageId, req.stageId(), callerId, actorName);
        }

        // Trigger achievement evaluation when ticket transitions to closed state
        if (ticket.getClosedAt() != null && ticket.getAssigneeId() != null) {
            achievementEvaluator.evaluateOnTicketClose(ticket.getAssigneeId());
        }

        eventPublisher.publishToTopic(
            "/topic/workspace/" + workspaceId + "/tickets",
            "TICKET_UPDATED",
            Map.of(
                "ticketId", ticketId.toString(),
                "stageId", ticket.getStageId() != null ? ticket.getStageId().toString() : "null"
            )
        );

        if (req.assigneeId() != null) {
            String prefix = keyPrefixFor(ticket.getProjectId());
            String code   = prefix + "-" + ticket.getTicketNumber();
            notificationService.notify(
                req.assigneeId(), workspaceId, "TICKET_ASSIGNED",
                Map.of(
                    "ticketId",   ticketId.toString(),
                    "ticketCode", code,
                    "title",      ticket.getTitle(),
                    "projectId",  ticket.getProjectId().toString()
                )
            );
        }

        return TicketResponse.from(ticket, keyPrefixFor(projectId));
    }

    @Transactional
    public TicketResponse claimTicket(UUID workspaceId, UUID projectId, UUID ticketId, UUID callerId) {
        requireMember(workspaceId, callerId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(TicketNotFoundException::new);

        if (!ticket.getProjectId().equals(projectId)) {
            throw new TicketNotFoundException();
        }

        if (ticket.getAssignmentMode() != AssignmentMode.OPEN_POOL || ticket.getAssigneeId() != null) {
            throw new TicketAlreadyClaimedException();
        }

        ticket.setAssigneeId(callerId);
        ticket.setAssignmentMode(AssignmentMode.ASSIGNED);
        ticket = ticketRepository.saveAndFlush(ticket);

        eventPublisher.publishToTopic(
            "/topic/workspace/" + workspaceId + "/tickets",
            "TICKET_CLAIMED",
            Map.of("ticketId", ticketId.toString(), "claimedBy", callerId.toString())
        );

        return TicketResponse.from(ticket, keyPrefixFor(projectId));
    }

    @Transactional
    public void deleteTicket(UUID workspaceId, UUID projectId, UUID ticketId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(TicketNotFoundException::new);

        if (!ticket.getProjectId().equals(projectId)) {
            throw new TicketNotFoundException();
        }

        ticketRepository.delete(ticket);

        eventPublisher.publishToTopic(
            "/topic/workspace/" + workspaceId + "/tickets",
            "TICKET_DELETED",
            Map.of("ticketId", ticketId.toString(), "projectId", projectId.toString())
        );
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Not a workspace member");
        }
    }

    private void requirePmOrAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
            .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (member.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can perform this action");
        }
    }
}
