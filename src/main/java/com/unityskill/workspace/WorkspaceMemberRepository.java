package com.unityskill.workspace;

import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findAllByUserId(UUID userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    List<WorkspaceMember> findAllByWorkspaceIdAndRoleIn(UUID workspaceId, List<WorkspaceRole> roles);

    /**
     * Story 8.2: Update last_inactivity_alerted_at WITHOUT triggering @UpdateTimestamp on updatedAt.
     * Using a targeted @Modifying @Query mirrors the InactivityDetector pattern (Story 4.4).
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkspaceMember m SET m.lastInactivityAlertedAt = :alertedAt WHERE m.id = :id")
    void updateLastInactivityAlertedAt(@Param("id") UUID id, @Param("alertedAt") Instant alertedAt);
}
