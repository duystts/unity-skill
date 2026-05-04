package com.unityskill.collaboration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meeting_transcripts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID meetingId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscriptStatus status = TranscriptStatus.UPLOADED;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
