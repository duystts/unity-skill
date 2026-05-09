package com.unityskill.tracking.entity;

import com.unityskill.tracking.ResourceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_permissions",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"user_id", "workspace_id", "resource_type", "resource_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(nullable = false, length = 255)
    private String resourceId;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
