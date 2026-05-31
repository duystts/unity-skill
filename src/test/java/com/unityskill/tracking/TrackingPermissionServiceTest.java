package com.unityskill.tracking;

import com.unityskill.tracking.entity.TrackingPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingPermissionServiceTest {

    @Mock TrackingPermissionRepository trackingPermissionRepository;
    @InjectMocks TrackingPermissionService trackingPermissionService;

    @Test
    void setPermission_newRecord_savesWithCorrectFields() {
        // AC1: POST creates a record when none exists
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(trackingPermissionRepository
                .findByUserIdAndWorkspaceIdAndResourceTypeAndResourceId(
                        userId, workspaceId, ResourceType.GITHUB_REPO, "owner/repo"))
                .thenReturn(Optional.empty());
        TrackingPermission saved = TrackingPermission.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .resourceType(ResourceType.GITHUB_REPO).resourceId("owner/repo")
                .enabled(false).build();
        when(trackingPermissionRepository.save(any())).thenReturn(saved);

        TrackingPermission result = trackingPermissionService.setPermission(
                userId, workspaceId, ResourceType.GITHUB_REPO, "owner/repo", false);

        ArgumentCaptor<TrackingPermission> captor = ArgumentCaptor.forClass(TrackingPermission.class);
        verify(trackingPermissionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getResourceType()).isEqualTo(ResourceType.GITHUB_REPO);
        assertThat(captor.getValue().getResourceId()).isEqualTo("owner/repo");
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(result).isEqualTo(saved);
    }

    @Test
    void setPermission_existingRecord_updatesEnabled() {
        // AC1/2: upsert — updates existing record rather than creating a duplicate
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        TrackingPermission existing = TrackingPermission.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .resourceType(ResourceType.GITHUB_REPO).resourceId("owner/repo")
                .enabled(true).build();
        when(trackingPermissionRepository
                .findByUserIdAndWorkspaceIdAndResourceTypeAndResourceId(
                        userId, workspaceId, ResourceType.GITHUB_REPO, "owner/repo"))
                .thenReturn(Optional.of(existing));
        when(trackingPermissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        trackingPermissionService.setPermission(
                userId, workspaceId, ResourceType.GITHUB_REPO, "owner/repo", false);

        ArgumentCaptor<TrackingPermission> captor = ArgumentCaptor.forClass(TrackingPermission.class);
        verify(trackingPermissionRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();  // flipped from true → false
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());  // same record
    }

    @Test
    void listPermissions_returnsAllForUserAndWorkspace() {
        // AC3: returns list of all permission records
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        List<TrackingPermission> perms = List.of(
                TrackingPermission.builder().id(UUID.randomUUID()).userId(userId)
                        .workspaceId(workspaceId).resourceType(ResourceType.GITHUB_REPO)
                        .resourceId("owner/repo").enabled(true).build(),
                TrackingPermission.builder().id(UUID.randomUUID()).userId(userId)
                        .workspaceId(workspaceId).resourceType(ResourceType.CHAT_CHANNEL)
                        .resourceId(UUID.randomUUID().toString()).enabled(false).build()
        );
        when(trackingPermissionRepository.findAllByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(perms);

        List<TrackingPermission> result = trackingPermissionService.listPermissions(userId, workspaceId);

        assertThat(result).hasSize(2);
        assertThat(result).containsAll(perms);
    }

    @Test
    void setPermission_chatChannel_savesWithChatChannelType() {
        // AC2: CHAT_CHANNEL resourceType persisted correctly
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String projectId = UUID.randomUUID().toString();
        when(trackingPermissionRepository
                .findByUserIdAndWorkspaceIdAndResourceTypeAndResourceId(
                        userId, workspaceId, ResourceType.CHAT_CHANNEL, projectId))
                .thenReturn(Optional.empty());
        when(trackingPermissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        trackingPermissionService.setPermission(
                userId, workspaceId, ResourceType.CHAT_CHANNEL, projectId, false);

        ArgumentCaptor<TrackingPermission> captor = ArgumentCaptor.forClass(TrackingPermission.class);
        verify(trackingPermissionRepository).save(captor.capture());
        assertThat(captor.getValue().getResourceType()).isEqualTo(ResourceType.CHAT_CHANNEL);
        assertThat(captor.getValue().getResourceId()).isEqualTo(projectId);
    }
}
