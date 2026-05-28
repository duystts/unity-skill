package com.unityskill.attachment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID ticketId;
    @Column(nullable = false) private UUID projectId;
    @Column(nullable = false) private UUID workspaceId;
    @Column(nullable = false) private UUID uploaderId;

    @Column(nullable = false)          private String fileName;
    @Column(nullable = false)          private String cloudinaryPublicId;
    @Column(nullable = false)          private String url;
    @Column(nullable = false)          private String resourceType; // image | video | raw
    @Column(nullable = false)          private long bytes;
    private String format;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
