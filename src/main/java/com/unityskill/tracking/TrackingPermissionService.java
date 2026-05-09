package com.unityskill.tracking;

import com.unityskill.tracking.entity.TrackingPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrackingPermissionService {

    private final TrackingPermissionRepository trackingPermissionRepository;

    /**
     * AC1/2: Upsert a tracking permission for a specific resource.
     * Creates on first call; updates on subsequent calls.
     */
    @Transactional
    public TrackingPermission setPermission(UUID userId, UUID workspaceId,
                                            ResourceType resourceType, String resourceId,
                                            boolean enabled) {
        TrackingPermission perm = trackingPermissionRepository
                .findByUserIdAndWorkspaceIdAndResourceTypeAndResourceId(
                        userId, workspaceId, resourceType, resourceId)
                .orElseGet(() -> TrackingPermission.builder()
                        .userId(userId)
                        .workspaceId(workspaceId)
                        .resourceType(resourceType)
                        .resourceId(resourceId)
                        .build());
        perm.setEnabled(enabled);
        return trackingPermissionRepository.save(perm);
    }

    /**
     * AC3: List all permission records for a user in a workspace.
     */
    @Transactional(readOnly = true)
    public List<TrackingPermission> listPermissions(UUID userId, UUID workspaceId) {
        return trackingPermissionRepository.findAllByUserIdAndWorkspaceId(userId, workspaceId);
    }
}
