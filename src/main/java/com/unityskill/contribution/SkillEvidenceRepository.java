package com.unityskill.contribution;

import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SkillEvidenceRepository extends JpaRepository<SkillEvidence, UUID> {

    List<SkillEvidence> findAllByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    List<SkillEvidence> findAllByUserIdAndStatus(UUID userId, EvidenceStatus status);

    List<SkillEvidence> findAllByUserIdAndWorkspaceIdAndStatus(UUID userId, UUID workspaceId, EvidenceStatus status);

    /** Story 7.3: returns all published evidence for a user across all workspaces. */
    List<SkillEvidence> findAllByUserIdAndIsPublishedTrue(UUID userId);

    /**
     * Story 7.5: used by StreakService to find all APPROVED evidence since a given instant.
     * Maps to: WHERE status = :status AND reviewed_at >= :since
     */
    List<SkillEvidence> findAllByStatusAndReviewedAtGreaterThanEqual(EvidenceStatus status, Instant since);

    /**
     * Story 9.4: fetch ALL skill evidence for a user across all workspaces, all statuses.
     * Used by DataExportService. Distinct from findAllByUserIdAndIsPublishedTrue (which is
     * for the public portfolio and only fetches published items).
     */
    List<SkillEvidence> findAllByUserId(UUID userId);

    /**
     * Story 9.5: permanently delete all skill evidence for a user.
     * Returns count of deleted rows for the deletion report.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SkillEvidence e WHERE e.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
