package com.unityskill.attachment.dto;

import com.unityskill.attachment.entity.TicketAttachment;

import java.time.Instant;
import java.util.UUID;

public record TicketAttachmentResponse(
    UUID id,
    UUID ticketId,
    UUID uploaderId,
    String fileName,
    String url,
    String resourceType,
    long bytes,
    String format,
    Instant createdAt
) {
    public static TicketAttachmentResponse from(TicketAttachment a) {
        return new TicketAttachmentResponse(
            a.getId(), a.getTicketId(), a.getUploaderId(),
            a.getFileName(), a.getUrl(), a.getResourceType(),
            a.getBytes(), a.getFormat(), a.getCreatedAt()
        );
    }
}
