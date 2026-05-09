package com.unityskill.portfolio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contribution_streaks",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_streak",
           columnNames = {"user_id", "workspace_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContributionStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private int currentStreakWeeks;

    @Column(nullable = false)
    private int longestStreakWeeks;

    /**
     * The ISO Monday of the most recently active week.
     * Used to detect consecutive vs. gap weeks during streak evaluation.
     */
    @Column(name = "last_activity_week", nullable = false)
    private LocalDate lastActivityWeek;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
