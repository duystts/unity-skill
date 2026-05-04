package com.unityskill.workspace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** Story 8.3: OVERLOADED threshold — members with > this many open tickets are OVERLOADED. Default 5. */
    @Builder.Default
    @Column(nullable = false)
    private int overloadedThreshold = 5;

    /** Story 8.3: BALANCED lower bound — members with >= this many open tickets are BALANCED. Default 2. */
    @Builder.Default
    @Column(nullable = false)
    private int balancedMinThreshold = 2;
}
