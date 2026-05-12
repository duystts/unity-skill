package com.unityskill.project.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID projectId;

    // nullable — ON DELETE SET NULL
    private UUID stageId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // nullable — set by Stories 3.5/3.6
    private UUID assigneeId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentMode assignmentMode = AssignmentMode.NONE;

    /** Sequential number within the project (1-based). Forms the ticket code: PROJECT_PREFIX-N */
    @Column(nullable = false)
    private int ticketNumber;

    private String githubPrUrl;

    private Instant closedAt;

    private Instant lastAlertedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
