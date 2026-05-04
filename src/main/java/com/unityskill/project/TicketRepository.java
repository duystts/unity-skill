package com.unityskill.project;

import com.unityskill.project.entity.AssignmentMode;
import com.unityskill.project.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByStageId(UUID stageId);

    List<Ticket> findAllByProjectId(UUID projectId);

    List<Ticket> findAllByWorkspaceIdAndAssigneeId(UUID workspaceId, UUID assigneeId);

    List<Ticket> findAllByProjectIdAndAssignmentMode(UUID projectId, AssignmentMode assignmentMode);

    List<Ticket> findAllByClosedAtIsNullAndUpdatedAtBefore(Instant updatedAt);

    List<Ticket> findAllByProjectIdAndClosedAtIsNull(UUID projectId);

    /**
     * Story 8.1: all open tickets in a workspace — used by TeamHealthService
     * for per-member open ticket count, last-activity derivation, and overdue count.
     */
    List<Ticket> findAllByWorkspaceIdAndClosedAtIsNull(UUID workspaceId);

    /**
     * Story 8.2: tickets in a workspace that were updated after a given instant AND have an assignee.
     * Used by InactivityAlertService to detect developer ticket activity.
     */
    List<Ticket> findAllByWorkspaceIdAndAssigneeIdIsNotNullAndUpdatedAtAfter(
            UUID workspaceId, Instant since);

    /**
     * Story 8.3: open tickets assigned to a specific member — used for the AC3 member detail view.
     */
    List<Ticket> findAllByWorkspaceIdAndAssigneeIdAndClosedAtIsNull(UUID workspaceId, UUID assigneeId);

    @Modifying
    @Transactional
    @Query("UPDATE Ticket t SET t.lastAlertedAt = :alertedAt WHERE t.id = :id")
    void updateLastAlertedAt(@Param("id") UUID id, @Param("alertedAt") Instant alertedAt);
}
