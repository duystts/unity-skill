package com.unityskill.collaboration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Enumerated(EnumType.STRING)
    private AgendaStatus agendaStatus;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // JSON array string e.g. ["Fix login bug","Update docs"] — populated by Story 5.5
    @Column(columnDefinition = "TEXT")
    private String actionItems;

    @Column(length = 2048)
    private String meetingUrl;

    // nullable — ON DELETE SET NULL
    private UUID createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
