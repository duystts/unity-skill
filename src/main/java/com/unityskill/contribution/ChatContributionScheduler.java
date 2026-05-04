package com.unityskill.contribution;

import com.unityskill.collaboration.ChatRepository;
import com.unityskill.collaboration.entity.ChatMessage;
import com.unityskill.tracking.ResourceType;
import com.unityskill.tracking.TrackingPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily scheduled job that analyzes chat messages for contribution signals.
 * <p>
 * Runs every 24 hours at midnight UTC (AC1). For each workspace that had chat activity
 * in the last 24 hours, groups messages by sender and delegates per-user analysis to
 * {@link ContributionService#extractFromChat} (async, fire-and-forget).
 * <p>
 * {@code @EnableScheduling} is provided by {@link com.unityskill.config.AsyncConfig}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatContributionScheduler {

    private final ChatRepository chatRepository;
    private final ContributionService contributionService;
    private final TrackingPermissionRepository trackingPermissionRepository;

    /**
     * Fetches all workspaces with chat activity in the last 24 hours,
     * groups messages by sender, and fires async contribution extraction per user.
     * <p>
     * AC1: runs every 24 hours at midnight UTC.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runAnalysis() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        log.info("ChatContributionScheduler: starting analysis for messages since {}", since);

        List<UUID> activeWorkspaceIds = chatRepository.findDistinctWorkspaceIdsSince(since);
        if (activeWorkspaceIds.isEmpty()) {
            log.debug("ChatContributionScheduler: no chat activity in last 24h");
            return;
        }

        for (UUID workspaceId : activeWorkspaceIds) {
            List<ChatMessage> messages = chatRepository
                    .findAllByWorkspaceIdAndCreatedAtAfter(workspaceId, since);

            // AC2 (Story 9.2): group by (projectId, senderId) to enable per-channel permission checks.
            // Skip messages with null sender (ON DELETE SET NULL) or null projectId.
            Map<UUID, Map<UUID, List<String>>> byChannelAndSender = messages.stream()
                    .filter(m -> m.getSenderId() != null && m.getProjectId() != null)
                    .collect(Collectors.groupingBy(
                            ChatMessage::getProjectId,
                            Collectors.groupingBy(
                                    ChatMessage::getSenderId,
                                    Collectors.mapping(ChatMessage::getContent, Collectors.toList())
                            )
                    ));

            byChannelAndSender.forEach((projectId, userMessages) ->
                userMessages.forEach((userId, contents) -> {
                    // AC2: skip if developer has disabled tracking for this channel (projectId)
                    if (trackingPermissionRepository.existsByUserIdAndResourceTypeAndResourceIdAndEnabledFalse(
                            userId, ResourceType.CHAT_CHANNEL, projectId.toString())) {
                        log.debug("Skipping chat analysis for user {} in channel {} — tracking disabled",
                                userId, projectId);
                        return;
                    }
                    String formatted = String.join("\n---\n", contents);
                    contributionService.extractFromChat(workspaceId, userId, formatted);
                })
            );

            log.debug("ChatContributionScheduler: processed workspace {}", workspaceId);
        }
    }
}
