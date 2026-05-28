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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock WorkspaceMemberRepository memberRepository;

    @InjectMocks ProjectService projectService;

    private WorkspaceMember memberWithRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
            .workspaceId(workspaceId)
            .userId(userId)
            .role(role)
            .build();
    }

    private Project buildProject(UUID workspaceId, ProjectVisibility visibility) {
        return Project.builder()
            .id(UUID.randomUUID())
            .workspaceId(workspaceId)
            .name("Test Project")
            .visibility(visibility)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void createProject_asPmRole_savesAndReturns() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project saved = buildProject(workspaceId, ProjectVisibility.PRIVATE);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.PM)));
        when(projectRepository.saveAndFlush(any(Project.class))).thenReturn(saved);

        ProjectResponse result = projectService.createProject(
            new CreateProjectRequest("Test Project", null, ProjectVisibility.PRIVATE),
            workspaceId, userId
        );

        assertThat(result.name()).isEqualTo("Test Project");
        verify(projectRepository).saveAndFlush(any(Project.class));
    }

    @Test
    void createProject_asAdminRole_savesAndReturns() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project saved = buildProject(workspaceId, ProjectVisibility.PUBLIC);

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(projectRepository.saveAndFlush(any(Project.class))).thenReturn(saved);

        ProjectResponse result = projectService.createProject(
            new CreateProjectRequest("Test Project", null, ProjectVisibility.PUBLIC),
            workspaceId, userId
        );

        assertThat(result.visibility()).isEqualTo("PUBLIC");
        verify(projectRepository).saveAndFlush(any(Project.class));
    }

    @Test
    void createProject_asDeveloperRole_throwsUnauthorized() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(memberWithRole(workspaceId, userId, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() -> projectService.createProject(
            new CreateProjectRequest("Test", null, ProjectVisibility.PRIVATE),
            workspaceId, userId
        )).isInstanceOf(UnauthorizedAccessException.class);

        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void listProjects_asMember_returnsList() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = buildProject(workspaceId, ProjectVisibility.PRIVATE);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(true);
        when(projectRepository.findAllByWorkspaceIdAndArchivedAtIsNull(workspaceId)).thenReturn(List.of(project));

        List<ProjectResponse> result = projectService.listProjects(workspaceId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workspaceId()).isEqualTo(workspaceId.toString());
    }

    @Test
    void listProjects_asNonMember_throwsUnauthorized() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(false);

        assertThatThrownBy(() -> projectService.listProjects(workspaceId, userId))
            .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getPublicProject_publicProject_returnsProject() {
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Project project = buildProject(workspaceId, ProjectVisibility.PUBLIC);

        when(projectRepository.findByIdAndVisibility(projectId, ProjectVisibility.PUBLIC))
            .thenReturn(Optional.of(project));

        ProjectResponse result = projectService.getPublicProject(projectId);

        assertThat(result.visibility()).isEqualTo("PUBLIC");
    }

    @Test
    void getPublicProject_privateProject_throwsNotFound() {
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findByIdAndVisibility(projectId, ProjectVisibility.PUBLIC))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getPublicProject(projectId))
            .isInstanceOf(ProjectNotFoundException.class);
    }
}
