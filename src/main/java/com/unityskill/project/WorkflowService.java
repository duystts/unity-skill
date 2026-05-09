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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowStageRepository stageRepository;
    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;

    @Transactional
    public StageResponse createStage(CreateStageRequest req, UUID workspaceId, UUID projectId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);

        WorkflowStage stage = WorkflowStage.builder()
                .projectId(projectId)
                .workspaceId(workspaceId)
                .name(req.name())
                .position(req.position())
                .isClosedState(req.isClosedState() != null && req.isClosedState())
                .build();

        stage = stageRepository.saveAndFlush(stage);
        return StageResponse.from(stage);
    }

    public List<StageResponse> listStages(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return stageRepository.findAllByProjectIdOrderByPositionAsc(projectId)
                .stream()
                .map(StageResponse::from)
                .toList();
    }

    @Transactional
    public StageResponse updateStage(UpdateStageRequest req, UUID workspaceId, UUID projectId, UUID stageId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);

        WorkflowStage stage = stageRepository.findByIdAndProjectId(stageId, projectId)
                .orElseThrow(StageNotFoundException::new);

        if (req.name() != null) stage.setName(req.name());
        if (req.position() != null) stage.setPosition(req.position());
        if (req.isClosedState() != null) stage.setClosedState(req.isClosedState());

        return StageResponse.from(stageRepository.saveAndFlush(stage));
    }

    @Transactional
    public void deleteStage(UUID workspaceId, UUID projectId, UUID stageId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);

        WorkflowStage stage = stageRepository.findByIdAndProjectId(stageId, projectId)
                .orElseThrow(StageNotFoundException::new);

        if (ticketRepository.existsByStageId(stageId)) {
            throw new StageHasActiveTicketsException();
        }

        stageRepository.delete(stage);
    }

    private void requirePmOrAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: not a workspace member"));
        if (member.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Access denied: PM or Admin role required");
        }
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }
    }
}
