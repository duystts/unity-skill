package com.unityskill.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebhookProcessor {

    private final WebhookEventHandler webhookEventHandler;

    @Async("taskExecutor")
    public void processAsync(String payloadJson, String eventType,
                              UUID workspaceId, UUID projectId, String repoFullName) {
        webhookEventHandler.handle(payloadJson, eventType, workspaceId, projectId, repoFullName);
    }
}
