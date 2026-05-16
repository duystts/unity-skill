package com.unityskill.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID projectId;

    private String repoFullName;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "webhook_status", nullable = false)
    private WebhookStatus status;

    private Instant processedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
