package com.unityskill.collaboration;

import com.unityskill.collaboration.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID projectId, Pageable pageable);

    /**
     * Fetches all messages in a given workspace sent after {@code since}.
     * Used by {@link com.unityskill.contribution.ChatContributionScheduler} to batch-scan a 24-hour window.
     * Spring Data JPA derives this query automatically from the method name.
     */
    List<ChatMessage> findAllByWorkspaceIdAndCreatedAtAfter(UUID workspaceId, Instant since);

    /**
     * Returns distinct workspace IDs that had chat activity after {@code since}.
     * Derived queries cannot return a {@code List<UUID>} directly, so JPQL is required.
     */
    @Query("SELECT DISTINCT m.workspaceId FROM ChatMessage m WHERE m.createdAt > :since")
    List<UUID> findDistinctWorkspaceIdsSince(@Param("since") Instant since);
}
