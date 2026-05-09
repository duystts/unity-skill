package com.unityskill.project;

import com.unityskill.common.exception.ProjectNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.dto.CreateProjectRequest;
import com.unityskill.project.dto.ProjectResponse;
import com.unityskill.project.entity.Project;
import com.unityskill.project.entity.ProjectVisibility;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository memberRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, UUID workspaceId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);

        Project project = Project.builder()
            .workspaceId(workspaceId)
            .name(req.name())
            .description(req.description())
            .visibility(req.visibility())
            .build();

        project = projectRepository.saveAndFlush(project);
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> listProjects(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return projectRepository.findAllByWorkspaceId(workspaceId)
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    public ProjectResponse getPublicProject(UUID projectId) {
        Project project = projectRepository
            .findByIdAndVisibility(projectId, ProjectVisibility.PUBLIC)
            .orElseThrow(ProjectNotFoundException::new);
        return ProjectResponse.from(project);
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
