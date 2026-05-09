package com.unityskill.contribution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_evidences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SkillEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceStatus status = EvidenceStatus.PENDING;

    /** AI-determined skill category, e.g. "Backend Development", "API Design" */
    @Column(nullable = false, length = 100)
    private String skillCategory;

    /** AI-generated 2–3 sentence description of the contribution evidence */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String aiSummary;

    /** Developer-edited notes — set during APPROVE+EDIT action (Story 6.5) */
    @Column(columnDefinition = "TEXT")
    private String developerNotes;

    /** JSON array of ContributionEvent UUIDs that produced this evidence */
    @Column(columnDefinition = "TEXT")
    private String sourceEvents;

    /** Set when developer approves or rejects (Story 6.5) */
    private Instant reviewedAt;

    /** Whether this evidence item is published to the developer's public portfolio (Story 7.2) */
    @Builder.Default
    @Column(nullable = false)
    private boolean isPublished = false;

    /** Set when developer publishes; cleared when unpublished (Story 7.2) */
    private Instant publishedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
