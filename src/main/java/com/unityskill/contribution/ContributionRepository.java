package com.unityskill.contribution;

import com.unityskill.contribution.entity.ContributionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ContributionRepository extends JpaRepository<ContributionEvent, UUID> {
    List<ContributionEvent> findAllByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * Story 8.2: contribution events in a workspace created after a given instant.
     * Covers PR events, meeting transcript events, and chat contribution events.
     * Used by InactivityAlertService to detect PR/transcript-based developer activity.
     */
    List<ContributionEvent> findAllByWorkspaceIdAndCreatedAtAfter(UUID workspaceId, Instant since);

    /**
     * Story 9.4: fetch ALL contribution events for a user across all workspaces.
     * Used by DataExportService to compile the personal data export.
     */
    List<ContributionEvent> findAllByUserId(UUID userId);

    /**
     * Story 9.5: permanently delete all contribution events for a user.
     * Returns count of deleted rows for the deletion report.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ContributionEvent e WHERE e.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
