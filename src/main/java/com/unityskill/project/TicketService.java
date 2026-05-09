package com.unityskill.project;

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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final NotificationService notificationService;

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest req, UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);

        Ticket ticket = Ticket.builder()
            .workspaceId(workspaceId)
            .projectId(projectId)
            .stageId(req.stageId())
            .title(req.title())
            .description(req.description())
            .build();

        ticket = ticketRepository.saveAndFlush(ticket);
        return TicketResponse.from(ticket);
    }

    public List<TicketResponse> listTickets(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return ticketRepository.findAllByProjectId(projectId).stream()
            .map(TicketResponse::from)
            .toList();
    }

    public List<TicketResponse> listOpenPoolTickets(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return ticketRepository.findAllByProjectIdAndAssignmentMode(projectId, AssignmentMode.OPEN_POOL)
            .stream().map(TicketResponse::from).toList();
    }

    public List<TicketResponse> listMyTickets(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return ticketRepository.findAllByWorkspaceIdAndAssigneeId(workspaceId, callerId)
            .stream().map(TicketResponse::from).toList();
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

        eventPublisher.publishToTopic(
            "/topic/workspace/" + workspaceId + "/tickets",
            "TICKET_UPDATED",
            Map.of(
                "ticketId", ticketId.toString(),
                "stageId", ticket.getStageId() != null ? ticket.getStageId().toString() : "null"
            )
        );

        if (req.assigneeId() != null) {
            notificationService.notify(
                req.assigneeId(), workspaceId, "TICKET_ASSIGNED",
                Map.of("ticketId", ticketId.toString(), "title", ticket.getTitle())
            );
        }

        return TicketResponse.from(ticket);
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

        return TicketResponse.from(ticket);
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
