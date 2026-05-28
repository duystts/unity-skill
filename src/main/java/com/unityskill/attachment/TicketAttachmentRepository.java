package com.unityskill.attachment;

import com.unityskill.attachment.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<TicketAttachment> findAllByWorkspaceId(UUID workspaceId);

    @Query("SELECT COALESCE(SUM(a.bytes), 0) FROM TicketAttachment a WHERE a.workspaceId = :workspaceId")
    long sumBytesByWorkspaceId(UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);
}
