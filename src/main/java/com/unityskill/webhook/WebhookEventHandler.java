package com.unityskill.webhook;

import com.unityskill.webhook.entity.WebhookEvent;
import com.unityskill.webhook.entity.WebhookStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEventHandler {

    private final WebhookEventRepository webhookEventRepository;
    private final GithubEventService githubEventService;

    @Retryable(retryFor = Exception.class, maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handle(String payloadJson, String eventType,
                       UUID workspaceId, UUID projectId, String repoFullName) {
        // AC 3: Persist with status = RECEIVED before processing
        WebhookEvent event = WebhookEvent.builder()
                .workspaceId(workspaceId)
                .projectId(projectId)
                .repoFullName(repoFullName)
                .eventType(eventType)
                .payloadJson(payloadJson)
                .status(WebhookStatus.RECEIVED)
                .build();
        event = webhookEventRepository.save(event);

        githubEventService.handleEvent(eventType, payloadJson, workspaceId, projectId);

        // AC 6: Update status to PROCESSED with timestamp
        event.setStatus(WebhookStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    @Recover
    public void recover(Exception ex, String payloadJson, String eventType,
                        UUID workspaceId, UUID projectId, String repoFullName) {
        // All retries exhausted — log the failure silently
        // Future stories can update event status to FAILED here
    }
}
