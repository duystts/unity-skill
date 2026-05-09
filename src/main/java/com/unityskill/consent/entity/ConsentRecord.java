package com.unityskill.consent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private Instant consentedAt;

    @Builder.Default
    @Column(nullable = false)
    private String consentVersion = "1.0";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
