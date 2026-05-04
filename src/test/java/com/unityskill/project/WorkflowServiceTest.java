package com.unityskill.project;

import com.unityskill.common.exception.StageHasActiveTicketsException;
import com.unityskill.common.exception.StageNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.dto.CreateStageRequest;
import com.unityskill.project.dto.StageResponse;
import com.unityskill.project.dto.UpdateStageRequest;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock WorkflowStageRepository stageRepository;
    @Mock TicketRepository ticketRepository;
    @Mock WorkspaceMemberRepository memberRepository;

    @InjectMocks WorkflowService workflowService;

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
            .isClosedState(false)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void createStage_asPmRole_savesAndReturns() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowStage saved = buildStage(projectId, workspaceId);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.saveAndFlush(any(WorkflowStage.class))).thenReturn(saved);

        StageResponse result = workflowService.createStage(
            new CreateStageRequest("In Progress", 1, null),
            workspaceId, projectId, userId
        );

        assertThat(result.name()).isEqualTo("In Progress");
        assertThat(result.position()).isEqualTo(1);
        verify(stageRepository).saveAndFlush(any(WorkflowStage.class));
    }

    @Test
    void createStage_asDeveloperRole_throwsForbidden() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() -> workflowService.createStage(
            new CreateStageRequest("Done", 2, true),
            workspaceId, projectId, userId
        )).isInstanceOf(UnauthorizedAccessException.class);

        verify(stageRepository, never()).saveAndFlush(any());
    }

    @Test
    void listStages_asMember_returnsOrderedList() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowStage stage = buildStage(projectId, workspaceId);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(stageRepository.findAllByProjectIdOrderByPositionAsc(projectId)).thenReturn(List.of(stage));

        List<StageResponse> result = workflowService.listStages(workspaceId, projectId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectId()).isEqualTo(projectId.toString());
    }

    @Test
    void updateStage_asPmRole_updatesFields() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowStage stage = buildStage(projectId, workspaceId);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.findByIdAndProjectId(stageId, projectId)).thenReturn(Optional.of(stage));
        when(stageRepository.saveAndFlush(any(WorkflowStage.class))).thenReturn(stage);

        StageResponse result = workflowService.updateStage(
            new UpdateStageRequest("Review", 2, true),
            workspaceId, projectId, stageId, userId
        );

        assertThat(result).isNotNull();
        verify(stageRepository).saveAndFlush(stage);
        assertThat(stage.getName()).isEqualTo("Review");
        assertThat(stage.getPosition()).isEqualTo(2);
        assertThat(stage.isClosedState()).isTrue();
    }

    @Test
    void updateStage_stageNotFound_throws() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.findByIdAndProjectId(stageId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowService.updateStage(
            new UpdateStageRequest("X", 0, false),
            workspaceId, projectId, stageId, userId
        )).isInstanceOf(StageNotFoundException.class);
    }

    @Test
    void deleteStage_withNoTickets_deletesStage() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowStage stage = buildStage(projectId, workspaceId);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(stageRepository.findByIdAndProjectId(stageId, projectId)).thenReturn(Optional.of(stage));
        when(ticketRepository.existsByStageId(stageId)).thenReturn(false);

        workflowService.deleteStage(workspaceId, projectId, stageId, userId);

        verify(stageRepository).delete(stage);
    }

    @Test
    void deleteStage_withActiveTickets_throwsException() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowStage stage = buildStage(projectId, workspaceId);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(stageRepository.findByIdAndProjectId(stageId, projectId)).thenReturn(Optional.of(stage));
        when(ticketRepository.existsByStageId(stageId)).thenReturn(true);

        assertThatThrownBy(() -> workflowService.deleteStage(workspaceId, projectId, stageId, userId))
            .isInstanceOf(StageHasActiveTicketsException.class);

        verify(stageRepository, never()).delete(any());
    }
}
