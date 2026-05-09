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
import com.unityskill.project.entity.WorkflowStage;
import com.unityskill.workspace.WorkspaceMemberRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock WorkflowStageRepository stageRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock WebSocketEventPublisher eventPublisher;
    @Mock NotificationService notificationService;

    @InjectMocks TicketService ticketService;

    private Ticket buildTicket(UUID workspaceId, UUID projectId, UUID stageId) {
        return Ticket.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .projectId(projectId)
            .stageId(stageId)
            .title("Sample ticket")
            .assignmentMode(AssignmentMode.NONE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private WorkflowStage buildStage(boolean isClosedState) {
        return WorkflowStage.builder()
            .id(UUID.randomUUID())
            .projectId(UUID.randomUUID())
            .workspaceId(UUID.randomUUID())
            .name("Stage")
            .position(0)
            .isClosedState(isClosedState)
            .createdAt(Instant.now())
            .build();
    }

    private WorkspaceMember memberWithRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
            .workspaceId(workspaceId)
            .userId(userId)
            .role(role)
            .build();
    }

    @Test
    void createTicket_asMember_savesAndReturns() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket saved = buildTicket(workspaceId, projectId, stageId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(saved);

        TicketResponse result = ticketService.createTicket(
            new CreateTicketRequest("Sample ticket", stageId, null),
            workspaceId, projectId, userId
        );

        assertThat(result.title()).isEqualTo("Sample ticket");
        verify(ticketRepository).saveAndFlush(any(Ticket.class));
    }

    @Test
    void createTicket_asNonMember_throwsUnauthorized() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.createTicket(
            new CreateTicketRequest("Test", null, null),
            workspaceId, projectId, userId
        )).isInstanceOf(UnauthorizedAccessException.class);

        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void listTickets_asMember_returnsList() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findAllByProjectId(projectId)).thenReturn(List.of(ticket));

        List<TicketResponse> result = ticketService.listTickets(workspaceId, projectId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectId()).isEqualTo(projectId.toString());
    }

    @Test
    void updateTicket_changesTitle_noClosedAt() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID currentStageId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, currentStageId);
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest("New title", null, null, null, null, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, userId);

        assertThat(ticket.getTitle()).isEqualTo("New title");
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void updateTicket_movedToClosedStage_setsClosedAt() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID closedStageId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        WorkflowStage closedStage = buildStage(true);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(stageRepository.findById(closedStageId)).thenReturn(Optional.of(closedStage));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, closedStageId, null, null, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, userId);

        assertThat(ticket.getStageId()).isEqualTo(closedStageId);
        assertThat(ticket.getClosedAt()).isNotNull();
    }

    @Test
    void updateTicket_movedToOpenStage_clearsClosedAt() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID openStageId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);
        ticket.setClosedAt(Instant.now());

        WorkflowStage openStage = buildStage(false);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(stageRepository.findById(openStageId)).thenReturn(Optional.of(openStage));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, openStageId, null, null, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, userId);

        assertThat(ticket.getStageId()).isEqualTo(openStageId);
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void updateTicket_ticketNotFound_throwsException() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.updateTicket(
            new UpdateTicketRequest(null, null, null, null, null, null),
            workspaceId, projectId, ticketId, userId
        )).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void updateTicket_publishesWebSocketEvent() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest("Updated", null, null, null, null, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, userId);

        verify(eventPublisher).publishToTopic(
            eq("/topic/workspace/" + workspaceId + "/tickets"),
            eq("TICKET_UPDATED"),
            any()
        );
    }

    // ─── Story 3.5: Assignment tests ───

    @Test
    void assignTicket_asPm_setsAssigneeAndNotifies() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, pmId)).thenReturn(true);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, pmId, WorkspaceRole.PM)));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, assigneeId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, null, assigneeId, AssignmentMode.ASSIGNED, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, pmId);

        assertThat(ticket.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(ticket.getAssignmentMode()).isEqualTo(AssignmentMode.ASSIGNED);
        verify(notificationService).notify(eq(assigneeId), eq(workspaceId), eq("TICKET_ASSIGNED"), any());
    }

    @Test
    void assignTicket_asDeveloper_throwsForbidden() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID devId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, devId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, devId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, devId, WorkspaceRole.DEVELOPER)));

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, null, assigneeId, null, null);

        assertThatThrownBy(() ->
            ticketService.updateTicket(req, workspaceId, projectId, ticketId, devId)
        ).isInstanceOf(UnauthorizedAccessException.class);

        verify(notificationService, never()).notify(any(), any(), any(), any());
    }

    @Test
    void assignTicket_toNonMember_throwsInvalidAssignee() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, pmId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, pmId, WorkspaceRole.PM)));
        // nonMemberId is NOT in workspace
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, nonMemberId)).thenReturn(false);

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, null, nonMemberId, null, null);

        assertThatThrownBy(() ->
            ticketService.updateTicket(req, workspaceId, projectId, ticketId, pmId)
        ).isInstanceOf(InvalidAssigneeException.class);
    }

    @Test
    void listMyTickets_returnsAssignedTickets() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setAssigneeId(userId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findAllByWorkspaceIdAndAssigneeId(workspaceId, userId))
            .thenReturn(List.of(ticket));

        List<TicketResponse> result = ticketService.listMyTickets(workspaceId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assigneeId()).isEqualTo(userId.toString());
    }

    // ─── Story 3.6: Open Pool tests ───

    @Test
    void openPool_asPm_clearsAssigneeAndSetsMode() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);
        ticket.setAssigneeId(UUID.randomUUID()); // pre-existing assignee

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, pmId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, pmId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, pmId, WorkspaceRole.PM)));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, null, null, AssignmentMode.OPEN_POOL, null);
        ticketService.updateTicket(req, workspaceId, projectId, ticketId, pmId);

        assertThat(ticket.getAssigneeId()).isNull();
        assertThat(ticket.getAssignmentMode()).isEqualTo(AssignmentMode.OPEN_POOL);
    }

    @Test
    void openPool_asDeveloper_throwsForbidden() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID devId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, devId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, devId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, devId, WorkspaceRole.DEVELOPER)));

        UpdateTicketRequest req = new UpdateTicketRequest(null, null, null, null, AssignmentMode.OPEN_POOL, null);

        assertThatThrownBy(() ->
            ticketService.updateTicket(req, workspaceId, projectId, ticketId, devId)
        ).isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void listOpenPoolTickets_filtersCorrectly() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setAssignmentMode(AssignmentMode.OPEN_POOL);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findAllByProjectIdAndAssignmentMode(projectId, AssignmentMode.OPEN_POOL))
            .thenReturn(List.of(ticket));

        List<TicketResponse> result = ticketService.listOpenPoolTickets(workspaceId, projectId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assignmentMode()).isEqualTo("OPEN_POOL");
    }

    @Test
    void claimTicket_asAnyMember_setsAssignee() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);
        ticket.setAssignmentMode(AssignmentMode.OPEN_POOL);
        ticket.setAssigneeId(null);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenReturn(ticket);

        ticketService.claimTicket(workspaceId, projectId, ticketId, userId);

        assertThat(ticket.getAssigneeId()).isEqualTo(userId);
        assertThat(ticket.getAssignmentMode()).isEqualTo(AssignmentMode.ASSIGNED);
        verify(eventPublisher).publishToTopic(
            eq("/topic/workspace/" + workspaceId + "/tickets"),
            eq("TICKET_CLAIMED"),
            any()
        );
    }

    @Test
    void claimTicket_alreadyClaimed_throws409() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Ticket ticket = buildTicket(workspaceId, projectId, UUID.randomUUID());
        ticket.setId(ticketId);
        ticket.setAssignmentMode(AssignmentMode.ASSIGNED); // not OPEN_POOL
        ticket.setAssigneeId(UUID.randomUUID());

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() ->
            ticketService.claimTicket(workspaceId, projectId, ticketId, userId)
        ).isInstanceOf(TicketAlreadyClaimedException.class);
    }
}
