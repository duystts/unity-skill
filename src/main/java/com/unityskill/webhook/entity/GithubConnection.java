package com.unityskill.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID workspaceId;

    private String repoFullName;

    @Column(columnDefinition = "TEXT")
    private String encryptedOauthToken;

    private String webhookSecret;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
