package com.unityskill.webhook;

import com.unityskill.webhook.entity.WebhookEvent;
import com.unityskill.webhook.entity.WebhookStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookEventHandlerTest {

    @Mock WebhookEventRepository webhookEventRepository;
    @Mock GithubEventService githubEventService;
    @InjectMocks WebhookEventHandler webhookEventHandler;

    @Test
    void handle_savesEventWithReceivedStatus() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String payloadJson = "{\"action\":\"opened\"}";
        String repoFullName = "owner/repo";

        WebhookEvent saved = WebhookEvent.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .status(WebhookStatus.RECEIVED)
                .payloadJson(payloadJson)
                .eventType("pull_request")
                .build();
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(saved);

        webhookEventHandler.handle(payloadJson, "pull_request", workspaceId, projectId, repoFullName);

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository, atLeast(1)).save(captor.capture());
        WebhookEvent firstSave = captor.getAllValues().get(0);
        assertThat(firstSave.getStatus()).isEqualTo(WebhookStatus.RECEIVED);
        assertThat(firstSave.getPayloadJson()).isEqualTo(payloadJson);
        assertThat(firstSave.getEventType()).isEqualTo("pull_request");
        assertThat(firstSave.getRepoFullName()).isEqualTo(repoFullName);
    }

    @Test
    void handle_updatesStatusToProcessed() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        WebhookEvent saved = WebhookEvent.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .status(WebhookStatus.RECEIVED)
                .payloadJson("{}")
                .eventType("pull_request")
                .build();
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(saved);

        webhookEventHandler.handle("{}", "pull_request", workspaceId, projectId, "owner/repo");

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository, times(2)).save(captor.capture());
        WebhookEvent secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(WebhookStatus.PROCESSED);
        assertThat(secondSave.getProcessedAt()).isNotNull();
    }

    @Test
    void handle_callsGithubEventService() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        WebhookEvent saved = WebhookEvent.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .status(WebhookStatus.RECEIVED)
                .payloadJson("{}")
                .eventType("pull_request")
                .build();
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(saved);

        webhookEventHandler.handle("{}", "pull_request", workspaceId, projectId, "owner/repo");

        verify(githubEventService).handleEvent("pull_request", "{}", workspaceId, projectId);
    }
}
