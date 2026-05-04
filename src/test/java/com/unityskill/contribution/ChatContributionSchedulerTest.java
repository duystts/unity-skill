package com.unityskill.contribution;

import com.unityskill.collaboration.ChatRepository;
import com.unityskill.collaboration.entity.ChatMessage;
import com.unityskill.tracking.ResourceType;
import com.unityskill.tracking.TrackingPermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatContributionSchedulerTest {

    @Mock ChatRepository chatRepository;
    @Mock ContributionService contributionService;
    @Mock TrackingPermissionRepository trackingPermissionRepository;
    @InjectMocks ChatContributionScheduler scheduler;

    /** Creates a message with a random projectId (channel). */
    private ChatMessage message(UUID workspaceId, UUID senderId, String content) {
        return message(workspaceId, UUID.randomUUID(), senderId, content);
    }

    /** Creates a message with an explicit projectId — needed when multiple messages must be in the same channel. */
    private ChatMessage message(UUID workspaceId, UUID projectId, UUID senderId, String content) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .senderId(senderId)
                .content(content)
                .build();
    }

    @Test
    void runAnalysis_messagesExist_dispatchesExtractionPerSender() {
        // AC1: messages found → extractFromChat called once per unique (channel, sender) pair
        UUID workspaceId   = UUID.randomUUID();
        UUID alice         = UUID.randomUUID();
        UUID bob           = UUID.randomUUID();
        UUID sharedChannel = UUID.randomUUID(); // fixed so alice's 2 messages group together

        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of(workspaceId));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of(
                        message(workspaceId, sharedChannel, alice, "Proposal: use JWT"),
                        message(workspaceId, sharedChannel, alice, "Refactor auth module"),
                        message(workspaceId, sharedChannel, bob,   "LGTM, let's go")
                ));

        scheduler.runAnalysis();

        // alice's messages concatenated into one call, bob's single message separate
        verify(contributionService).extractFromChat(eq(workspaceId), eq(alice), contains("JWT"));
        verify(contributionService).extractFromChat(eq(workspaceId), eq(bob), eq("LGTM, let's go"));
    }

    @Test
    void runAnalysis_noActiveWorkspaces_skipsExtraction() {
        // AC1: no recent chat activity → nothing dispatched
        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of());

        scheduler.runAnalysis();

        verifyNoInteractions(contributionService);
        verify(chatRepository, never()).findAllByWorkspaceIdAndCreatedAtAfter(any(), any());
    }

    @Test
    void runAnalysis_nullSenderMessages_skipped() {
        // ChatMessage.senderId nullable (ON DELETE SET NULL) — must not dispatch for null sender
        UUID workspaceId = UUID.randomUUID();
        UUID alice       = UUID.randomUUID();
        UUID channel     = UUID.randomUUID();

        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of(workspaceId));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of(
                        message(workspaceId, channel, null,  "anonymous message"),  // senderId null — skip
                        message(workspaceId, channel, alice, "valid message")
                ));

        scheduler.runAnalysis();

        // Only alice dispatched — no NPE, no call for null sender
        verify(contributionService, times(1))
                .extractFromChat(eq(workspaceId), eq(alice), anyString());
    }

    @Test
    void runAnalysis_multipleSenderMessages_concatenatedWithSeparator() {
        // AC1: multiple messages from same sender in the same channel are joined with "\n---\n"
        UUID workspaceId = UUID.randomUUID();
        UUID alice       = UUID.randomUUID();
        UUID channel     = UUID.randomUUID(); // fixed so both messages fall into the same group

        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of(workspaceId));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of(
                        message(workspaceId, channel, alice, "First message"),
                        message(workspaceId, channel, alice, "Second message")
                ));

        scheduler.runAnalysis();

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(contributionService).extractFromChat(eq(workspaceId), eq(alice), contentCaptor.capture());
        String captured = contentCaptor.getValue();
        assertThat(captured).contains("First message");
        assertThat(captured).contains("Second message");
        assertThat(captured).contains("\n---\n");
    }

    @Test
    void runAnalysis_multipleWorkspaces_processesEach() {
        // AC1: each workspace is processed independently
        UUID ws1     = UUID.randomUUID();
        UUID ws2     = UUID.randomUUID();
        UUID user1   = UUID.randomUUID();
        UUID user2   = UUID.randomUUID();
        UUID channel = UUID.randomUUID();

        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of(ws1, ws2));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(ws1), any()))
                .thenReturn(List.of(message(ws1, channel, user1, "Workspace 1 message")));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(ws2), any()))
                .thenReturn(List.of(message(ws2, channel, user2, "Workspace 2 message")));

        scheduler.runAnalysis();

        verify(contributionService).extractFromChat(eq(ws1), eq(user1), anyString());
        verify(contributionService).extractFromChat(eq(ws2), eq(user2), anyString());
    }

    @Test
    void runAnalysis_channelTrackingDisabled_skipsDisabledChannel() {
        // AC2 (Story 9.2): messages in a channel where tracking is disabled are not dispatched
        UUID workspaceId     = UUID.randomUUID();
        UUID alice           = UUID.randomUUID();
        UUID disabledChannel = UUID.randomUUID();
        UUID enabledChannel  = UUID.randomUUID();

        when(chatRepository.findDistinctWorkspaceIdsSince(any())).thenReturn(List.of(workspaceId));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of(
                        message(workspaceId, disabledChannel, alice, "Message in disabled channel"),
                        message(workspaceId, enabledChannel,  alice, "Message in enabled channel")
                ));
        // Must stub BOTH calls explicitly — Mockito strict mode throws PotentialStubbingProblem
        // if a method is called with args that partially match a stub but don't match exactly
        when(trackingPermissionRepository
                .existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
                        alice, ResourceType.CHAT_CHANNEL, disabledChannel.toString()))
                .thenReturn(true);   // disabled — skip
        when(trackingPermissionRepository
                .existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
                        alice, ResourceType.CHAT_CHANNEL, enabledChannel.toString()))
                .thenReturn(false);  // enabled — dispatch

        scheduler.runAnalysis();

        // Only the enabled-channel message is dispatched
        verify(contributionService, times(1))
                .extractFromChat(eq(workspaceId), eq(alice), anyString());
        verify(contributionService)
                .extractFromChat(eq(workspaceId), eq(alice), contains("enabled channel"));
    }
}
