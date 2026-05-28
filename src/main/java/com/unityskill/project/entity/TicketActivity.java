package com.unityskill.project.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ticketId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID workspaceId;

    /** null = system / GitHub automation */
    private UUID actorId;

    /** Denormalized display name at the time of the action */
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    /** Populated for STAGE_CHANGED */
    private UUID fromStageId;

    /** Populated for STAGE_CHANGED */
    private UUID toStageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
