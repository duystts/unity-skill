package com.unityskill.project;

import com.unityskill.common.exception.InvalidTriggerException;
import com.unityskill.common.exception.StageNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.TicketActivityService;
import com.unityskill.project.dto.CreateTriggerRuleRequest;
import com.unityskill.project.dto.TriggerRuleResponse;
import com.unityskill.project.entity.AutoTriggerRule;
import com.unityskill.project.entity.Ticket;
import com.unityskill.project.entity.TriggerType;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerServiceTest {

    @Mock AutoTriggerRuleRepository ruleRepository;
    @Mock WorkflowStageRepository stageRepository;
    @Mock TicketRepository ticketRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock WebSocketEventPublisher eventPublisher;
    @Mock TicketActivityService ticketActivityService;

    @InjectMocks TriggerService triggerService;

    private WorkspaceMember memberWithRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
            .workspaceId(workspaceId)
            .userId(userId)
            .role(role)
            .build();
    }

    private WorkflowStage buildStage(UUID projectId, UUID workspaceId) {
        return WorkflowStage.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .workspaceId(workspaceId)
            .name("In Progress")
            .position(1)
            .createdAt(Instant.now())
            .build();
    }

    private AutoTriggerRule buildRule(UUID projectId, UUID sourceStageId, UUID targetStageId) {
        return AutoTriggerRule.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .workspaceId(UUID.randomUUID())
            .triggerType(TriggerType.PR_OPENED)
            .sourceStageId(sourceStageId)
            .targetStageId(targetStageId)
            .build();
    }

    @Test
    void createRule_asPmRole_savesAndReturns() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sourceStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AutoTriggerRule saved = buildRule(projectId, sourceStageId, targetStageId);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.findByIdAndProjectId(sourceStageId, projectId))
            .thenReturn(Optional.of(buildStage(projectId, workspaceId)));
        when(stageRepository.findByIdAndProjectId(targetStageId, projectId))
            .thenReturn(Optional.of(buildStage(projectId, workspaceId)));
        when(ruleRepository.saveAndFlush(any(AutoTriggerRule.class))).thenReturn(saved);

        TriggerRuleResponse result = triggerService.createRule(
            new CreateTriggerRuleRequest(TriggerType.PR_OPENED, targetStageId),
            workspaceId, projectId, sourceStageId, userId
        );

        assertThat(result.triggerType()).isEqualTo("PR_OPENED");
        verify(ruleRepository).saveAndFlush(any(AutoTriggerRule.class));
    }

    @Test
    void createRule_asDeveloperRole_throwsForbidden() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() -> triggerService.createRule(
            new CreateTriggerRuleRequest(TriggerType.PR_OPENED, UUID.randomUUID()),
            workspaceId, projectId, UUID.randomUUID(), userId
        )).isInstanceOf(UnauthorizedAccessException.class);

        verify(ruleRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRule_targetStageDifferentProject_throwsInvalidTrigger() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sourceStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.findByIdAndProjectId(sourceStageId, projectId))
            .thenReturn(Optional.of(buildStage(projectId, workspaceId)));
        // targetStageId not found in this project → simulates different project
        when(stageRepository.findByIdAndProjectId(targetStageId, projectId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> triggerService.createRule(
            new CreateTriggerRuleRequest(TriggerType.PR_OPENED, targetStageId),
            workspaceId, projectId, sourceStageId, userId
        )).isInstanceOf(InvalidTriggerException.class)
          .hasMessageContaining("different project");
    }

    @Test
    void listRules_asMember_returnsList() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AutoTriggerRule rule = buildRule(projectId, UUID.randomUUID(), UUID.randomUUID());

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(ruleRepository.findAllByProjectId(projectId)).thenReturn(List.of(rule));

        List<TriggerRuleResponse> result = triggerService.listRules(workspaceId, projectId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectId()).isEqualTo(projectId.toString());
    }

    @Test
    void evaluate_matchingStage_transitionsTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setProjectId(projectId);
        ticket.setStageId(currentStageId);

        AutoTriggerRule rule = buildRule(projectId, currentStageId, targetStageId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ruleRepository.findAllByProjectIdAndTriggerType(projectId, TriggerType.PR_OPENED))
            .thenReturn(List.of(rule));
        // stageRepository.findById returns empty → closedAt not set (non-closed target)
        when(stageRepository.findById(targetStageId)).thenReturn(Optional.empty());

        triggerService.evaluate(TriggerType.PR_OPENED, ticketId);

        assertThat(ticket.getStageId()).isEqualTo(targetStageId);
        verify(ticketRepository).save(ticket);
        // AC 2: WebSocket event published on transition
        verify(eventPublisher).publishToTopic(
            any(String.class),
            eq("TICKET_STAGE_CHANGED"),
            any(Map.class)
        );
    }

    @Test
    void evaluate_sourceStageNull_alwaysTransitions() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID anyStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setProjectId(projectId);
        ticket.setStageId(anyStageId);

        // null sourceStageId = applies to any stage
        AutoTriggerRule rule = buildRule(projectId, null, targetStageId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ruleRepository.findAllByProjectIdAndTriggerType(projectId, TriggerType.PR_MERGED))
            .thenReturn(List.of(rule));
        when(stageRepository.findById(targetStageId)).thenReturn(Optional.empty());

        triggerService.evaluate(TriggerType.PR_MERGED, ticketId);

        assertThat(ticket.getStageId()).isEqualTo(targetStageId);
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishToTopic(any(String.class), eq("TICKET_STAGE_CHANGED"), any(Map.class));
    }

    @Test
    void evaluate_nonMatchingStage_noOp() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentStageId = UUID.randomUUID();
        UUID differentStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setProjectId(projectId);
        ticket.setStageId(currentStageId);

        // rule's sourceStageId is different from ticket's current stage
        AutoTriggerRule rule = buildRule(projectId, differentStageId, targetStageId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ruleRepository.findAllByProjectIdAndTriggerType(projectId, TriggerType.PR_CLOSED))
            .thenReturn(List.of(rule));

        triggerService.evaluate(TriggerType.PR_CLOSED, ticketId);

        // stageId should NOT have changed
        assertThat(ticket.getStageId()).isEqualTo(currentStageId);
        verify(ticketRepository, never()).save(any());
        // AC 3: no WebSocket event on no-op
        verify(eventPublisher, never()).publishToTopic(any(), any(), any());
    }

    @Test
    void evaluate_closedStateTarget_setsClosedAt() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID currentStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setProjectId(projectId);
        ticket.setWorkspaceId(workspaceId);
        ticket.setStageId(currentStageId);

        AutoTriggerRule rule = buildRule(projectId, currentStageId, targetStageId);
        WorkflowStage closedStage = WorkflowStage.builder()
            .id(targetStageId)
            .projectId(projectId)
            .workspaceId(workspaceId)
            .name("Done")
            .position(5)
            .isClosedState(true)
            .createdAt(Instant.now())
            .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ruleRepository.findAllByProjectIdAndTriggerType(projectId, TriggerType.PR_OPENED))
            .thenReturn(List.of(rule));
        when(stageRepository.findById(targetStageId)).thenReturn(Optional.of(closedStage));

        triggerService.evaluate(TriggerType.PR_OPENED, ticketId);

        assertThat(ticket.getStageId()).isEqualTo(targetStageId);
        assertThat(ticket.getClosedAt()).isNotNull();   // AC 4
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishToTopic(any(String.class), eq("TICKET_STAGE_CHANGED"), any(Map.class));
    }

    @Test
    void evaluate_nonClosedStateTarget_doesNotSetClosedAt() {
        UUID ticketId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID currentStageId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setProjectId(projectId);
        ticket.setWorkspaceId(workspaceId);
        ticket.setStageId(currentStageId);

        AutoTriggerRule rule = buildRule(projectId, currentStageId, targetStageId);
        WorkflowStage openStage = WorkflowStage.builder()
            .id(targetStageId)
            .projectId(projectId)
            .workspaceId(workspaceId)
            .name("In Review")
            .position(3)
            .isClosedState(false)
            .createdAt(Instant.now())
            .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ruleRepository.findAllByProjectIdAndTriggerType(projectId, TriggerType.PR_REVIEWED))
            .thenReturn(List.of(rule));
        when(stageRepository.findById(targetStageId)).thenReturn(Optional.of(openStage));

        triggerService.evaluate(TriggerType.PR_REVIEWED, ticketId);

        assertThat(ticket.getStageId()).isEqualTo(targetStageId);
        assertThat(ticket.getClosedAt()).isNull();   // AC 4 boundary: non-closed stage → no closedAt
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishToTopic(any(String.class), eq("TICKET_STAGE_CHANGED"), any(Map.class));
    }
}
