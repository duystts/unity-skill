package com.unityskill.privacy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_exports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportStatus status = ExportStatus.IN_PROGRESS;

    /** Null until status = READY. May be large (all user contribution data). */
    @Column(columnDefinition = "TEXT")
    private String exportJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when export compilation completes (status transitions to READY). */
    private Instant completedAt;
}
