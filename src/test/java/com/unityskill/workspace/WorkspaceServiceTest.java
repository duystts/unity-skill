package com.unityskill.workspace;

import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.workspace.dto.CreateWorkspaceRequest;
import com.unityskill.workspace.dto.WorkspaceResponse;
import com.unityskill.workspace.entity.Workspace;
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
class WorkspaceServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;

    @InjectMocks WorkspaceService workspaceService;

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private Workspace buildWorkspace() {
        return Workspace.builder()
                .id(WORKSPACE_ID)
                .name("Test Team")
                .description("A test workspace")
                .slug("test-team-abcd1234")
                .createdBy(CREATOR_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createWorkspace_savesWorkspaceAndReturnsResponse() {
        var req = new CreateWorkspaceRequest("Test Team", "A test workspace");
        var saved = buildWorkspace();
        when(workspaceRepository.saveAndFlush(any(Workspace.class))).thenReturn(saved);
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenReturn(new WorkspaceMember());

        WorkspaceResponse result = workspaceService.createWorkspace(req, CREATOR_ID);

        assertThat(result.name()).isEqualTo("Test Team");
        assertThat(result.id()).isEqualTo(WORKSPACE_ID.toString());
        verify(workspaceRepository).saveAndFlush(any(Workspace.class));
    }

    @Test
    void createWorkspace_savesAdminMember() {
        var req = new CreateWorkspaceRequest("Test Team", null);
        when(workspaceRepository.saveAndFlush(any(Workspace.class))).thenReturn(buildWorkspace());
        var memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        when(workspaceMemberRepository.save(memberCaptor.capture())).thenReturn(new WorkspaceMember());

        workspaceService.createWorkspace(req, CREATOR_ID);

        WorkspaceMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRole()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(savedMember.getUserId()).isEqualTo(CREATOR_ID);
        assertThat(savedMember.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void createWorkspace_generatesSlugFromName() {
        var req = new CreateWorkspaceRequest("My Awesome Team", null);
        var workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        when(workspaceRepository.saveAndFlush(workspaceCaptor.capture())).thenReturn(buildWorkspace());
        when(workspaceMemberRepository.save(any())).thenReturn(new WorkspaceMember());

        workspaceService.createWorkspace(req, CREATOR_ID);

        String slug = workspaceCaptor.getValue().getSlug();
        assertThat(slug).startsWith("my-awesome-team-");
        assertThat(slug).matches("[a-z0-9-]+");
    }

    @Test
    void listWorkspaces_returnsEmptyListIfNoMemberships() {
        when(workspaceMemberRepository.findAllByUserId(CREATOR_ID)).thenReturn(List.of());

        List<WorkspaceResponse> result = workspaceService.listWorkspaces(CREATOR_ID);

        assertThat(result).isEmpty();
        verify(workspaceRepository, never()).findAllById(any());
    }

    @Test
    void listWorkspaces_returnsWorkspacesForUser() {
        var member = WorkspaceMember.builder()
                .workspaceId(WORKSPACE_ID).userId(CREATOR_ID).role(WorkspaceRole.ADMIN).build();
        when(workspaceMemberRepository.findAllByUserId(CREATOR_ID)).thenReturn(List.of(member));
        when(workspaceRepository.findAllById(List.of(WORKSPACE_ID))).thenReturn(List.of(buildWorkspace()));

        List<WorkspaceResponse> result = workspaceService.listWorkspaces(CREATOR_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(WORKSPACE_ID.toString());
    }

    @Test
    void getWorkspace_throwsWorkspaceNotFoundIfNotMember() {
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, CREATOR_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> workspaceService.getWorkspace(WORKSPACE_ID, CREATOR_ID))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void getWorkspace_returnsWorkspaceIfMember() {
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, CREATOR_ID))
                .thenReturn(true);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(buildWorkspace()));

        WorkspaceResponse result = workspaceService.getWorkspace(WORKSPACE_ID, CREATOR_ID);

        assertThat(result.id()).isEqualTo(WORKSPACE_ID.toString());
        assertThat(result.name()).isEqualTo("Test Team");
    }
}
