package com.unityskill.achievement;

import com.unityskill.achievement.entity.TicketTag;
import com.unityskill.achievement.entity.TicketTagValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketTagRepository extends JpaRepository<TicketTag, UUID> {

    List<TicketTag> findAllByTicketId(UUID ticketId);

    /** Batch-fetch tags for multiple tickets at once — used to include tags in TicketResponse lists. */
    List<TicketTag> findAllByTicketIdIn(List<UUID> ticketIds);

    boolean existsByTicketIdAndTag(UUID ticketId, TicketTagValue tag);

    /**
     * Counts distinct closed tickets (assigned to the given user) that carry a specific tag.
     * Uses a native join because TicketTag has no JPA association to Ticket.
     */
    @Query(value = """
        SELECT COUNT(DISTINCT tt.ticket_id)
        FROM ticket_tags tt
        JOIN tickets t ON t.id = tt.ticket_id
        WHERE t.assignee_id = :userId
          AND t.closed_at IS NOT NULL
          AND tt.tag = :tag
        """, nativeQuery = true)
    long countClosedTaggedTicketsForUser(@Param("userId") UUID userId, @Param("tag") String tag);
}
