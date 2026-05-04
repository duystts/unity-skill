package com.unityskill.portfolio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endorsements",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_endorsement",
           columnNames = {"evidence_id", "endorser_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Endorsement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "endorser_id", nullable = false)
    private UUID endorserId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
