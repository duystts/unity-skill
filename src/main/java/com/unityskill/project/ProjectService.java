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

import java.time.Instant;
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
            .keyPrefix(generateKeyPrefix(req.name()))
            .build();

        project = projectRepository.saveAndFlush(project);
        return ProjectResponse.from(project);
    }

    /**
     * Auto-generates a short uppercase prefix from the project name.
     * Takes the first letter of each word (max 6 chars).
     * Examples: "Unity Skill" → "US", "Backend API" → "BA", "MyProject" → "MYPRO"
     */
    private static String generateKeyPrefix(String name) {
        String[] words = name.trim().split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
            if (sb.length() >= 6) break;
        }
        if (sb.isEmpty()) {
            // Fallback: take first 4 uppercase letters from the name
            name.chars()
                .filter(Character::isLetter)
                .limit(4)
                .forEach(c -> sb.append(Character.toUpperCase((char) c)));
        }
        return sb.isEmpty() ? "PROJ" : sb.toString();
    }

    public ProjectResponse getProject(UUID workspaceId, UUID projectId, UUID callerId) {
        requireMember(workspaceId, callerId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(ProjectNotFoundException::new);
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> listProjects(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return projectRepository.findAllByWorkspaceIdAndArchivedAtIsNull(workspaceId)
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    public List<ProjectResponse> listArchivedProjects(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return projectRepository.findAllByWorkspaceIdAndArchivedAtIsNotNull(workspaceId)
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    @Transactional
    public ProjectResponse archiveProject(UUID workspaceId, UUID projectId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(ProjectNotFoundException::new);
        project.setArchivedAt(Instant.now());
        project = projectRepository.saveAndFlush(project);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse unarchiveProject(UUID workspaceId, UUID projectId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(ProjectNotFoundException::new);
        project.setArchivedAt(null);
        project = projectRepository.saveAndFlush(project);
        return ProjectResponse.from(project);
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
