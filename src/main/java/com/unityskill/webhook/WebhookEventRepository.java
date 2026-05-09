package com.unityskill.webhook;

import com.unityskill.webhook.entity.WebhookEvent;
import com.unityskill.webhook.entity.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    List<WebhookEvent> findByWorkspaceIdAndStatus(UUID workspaceId, WebhookStatus status);
}
