package com.unityskill.workspace;

import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.workspace.dto.CreateWorkspaceRequest;
import com.unityskill.workspace.dto.WorkspaceResponse;
import com.unityskill.workspace.dto.WorkspaceSettingsRequest;
import com.unityskill.workspace.dto.WorkspaceSettingsResponse;
import com.unityskill.workspace.entity.Workspace;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest req, UUID creatorId) {
        Workspace workspace = Workspace.builder()
                .name(req.name())
                .description(req.description())
                .slug(generateSlug(req.name()))
                .createdBy(creatorId)
                .build();
        workspace = workspaceRepository.saveAndFlush(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(creatorId)
                .role(WorkspaceRole.ADMIN)
                .build();
        workspaceMemberRepository.save(member);

        return WorkspaceResponse.from(workspace);
    }

    public List<WorkspaceResponse> listWorkspaces(UUID userId) {
        List<UUID> workspaceIds = workspaceMemberRepository.findAllByUserId(userId)
                .stream().map(WorkspaceMember::getWorkspaceId).toList();
        if (workspaceIds.isEmpty()) return List.of();
        return workspaceRepository.findAllById(workspaceIds).stream()
                .map(WorkspaceResponse::from).toList();
    }

    public WorkspaceResponse getWorkspace(UUID workspaceId, UUID userId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new WorkspaceNotFoundException();
        }
        return WorkspaceResponse.from(
                workspaceRepository.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new)
        );
    }

    /**
     * Story 8.3 AC2: Allow PM/Admin to customize workload thresholds per workspace.
     * Saves thresholds to the workspace row; subsequent getWorkload() reads them.
     */
    @Transactional
    public WorkspaceSettingsResponse updateSettings(UUID workspaceId, UUID callerId,
                                                    WorkspaceSettingsRequest request) {
        WorkspaceMember caller = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId)
                .orElseThrow(() -> new UnauthorizedAccessException("Not a workspace member"));
        if (caller.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Only PM or Admin can update workspace settings");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);

        workspace.setOverloadedThreshold(request.overloadedThreshold());
        workspace.setBalancedMinThreshold(request.balancedMinThreshold());
        workspaceRepository.save(workspace);

        return new WorkspaceSettingsResponse(
                workspace.getOverloadedThreshold(),
                workspace.getBalancedMinThreshold()
        );
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "workspace";
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
