package com.unityskill.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contribution_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContributionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType sourceType;

    /** For MEETING events: the transcript UUID. Nullable for other source types. */
    private UUID sourceRefId;

    /** JSON array of skill strings, e.g. ["Java","Spring Boot","API Design"] */
    @Column(columnDefinition = "TEXT")
    private String skillSignals;

    /** When the AI analysis ran for this contribution. */
    @Column(nullable = false)
    private Instant processedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
