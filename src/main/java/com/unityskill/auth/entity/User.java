package com.unityskill.auth.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash;          // null for OAuth users

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    @Column(name = "github_id", unique = true)
    private String githubId;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Builder.Default
    @Column(nullable = false)
    private boolean isIncognito = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UiMode uiMode = UiMode.CHARACTER;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
