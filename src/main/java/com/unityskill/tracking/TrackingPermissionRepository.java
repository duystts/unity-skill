package com.unityskill.tracking;

import com.unityskill.tracking.entity.TrackingPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackingPermissionRepository extends JpaRepository<TrackingPermission, UUID> {

    /**
     * AC4: default-permit check — returns true ONLY if user explicitly disabled this resource.
     * Used by ContributionService (GITHUB_REPO) and ChatContributionScheduler (CHAT_CHANNEL).
     * No record → returns false → tracking IS permitted.
     */
    boolean existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
            UUID userId, ResourceType resourceType, String resourceId);

    /** AC3: list all permissions for a user in a workspace. */
    List<TrackingPermission> findAllByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    /** AC1/2: find existing record to update (upsert pattern). */
    Optional<TrackingPermission> findByUserIdAndWorkspaceIdAndResourceTypeAndResourceId(
            UUID userId, UUID workspaceId, ResourceType resourceType, String resourceId);

    /**
     * Story 9.5: permanently delete all tracking permissions for a user.
     * Returns count of deleted rows for the deletion report.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TrackingPermission t WHERE t.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
