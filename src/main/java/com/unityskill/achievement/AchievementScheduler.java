package com.unityskill.achievement;

import com.unityskill.ai.AiProvider;
import com.unityskill.collaboration.ChatRepository;
import com.unityskill.collaboration.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Weekly job (Sunday 23:00 UTC) that runs AI-based chat achievement evaluation.
 * Analyzes each user's messages from the last 30 days and awards chat achievements.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementScheduler {

    private final ChatRepository chatRepository;
    private final AchievementEvaluator achievementEvaluator;
    private final AiProvider aiProvider;

    @Scheduled(cron = "0 0 23 * * SUN")
    public void evaluateChatAchievements() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        log.info("AchievementScheduler: starting chat evaluation since {}", since);

        List<UUID> activeWorkspaces = chatRepository.findDistinctWorkspaceIdsSince(since);
        if (activeWorkspaces.isEmpty()) return;

        // Collect per-user messages across all active workspaces
        Map<UUID, List<String>> messagesByUser = new HashMap<>();
        for (UUID workspaceId : activeWorkspaces) {
            chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(workspaceId, since)
                .stream()
                .filter(m -> m.getSenderId() != null)
                .forEach(m -> messagesByUser
                    .computeIfAbsent(m.getSenderId(), k -> new ArrayList<>())
                    .add(m.getContent()));
        }

        for (Map.Entry<UUID, List<String>> entry : messagesByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<String> messages = entry.getValue();
            try {
                evaluateUserChatAchievements(userId, messages);
            } catch (Exception e) {
                log.warn("Chat achievement eval failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private void evaluateUserChatAchievements(UUID userId, List<String> messages) {
        String prompt = buildEvalPrompt(messages);
        String response = aiProvider.generateText(prompt);

        if (response == null) return;
        String lower = response.toLowerCase();

        if (lower.contains("team_voice:true")) {
            achievementEvaluator.awardChatAchievement(userId, "team_voice",
                Map.of("messageCount", messages.size()));
        }
        if (lower.contains("problem_solver:true")) {
            achievementEvaluator.awardChatAchievement(userId, "problem_solver",
                Map.of("evidence", extractSnippet(response, "problem_solver")));
        }
        if (lower.contains("mentor:true")) {
            achievementEvaluator.awardChatAchievement(userId, "mentor",
                Map.of("evidence", extractSnippet(response, "mentor")));
        }
        if (lower.contains("decision_maker:true")) {
            achievementEvaluator.awardChatAchievement(userId, "decision_maker",
                Map.of("evidence", extractSnippet(response, "decision_maker")));
        }
    }

    private String buildEvalPrompt(List<String> messages) {
        String joined = messages.stream()
            .limit(200)
            .collect(Collectors.joining("\n---\n"));

        return """
            You are evaluating a software developer's chat messages to determine achievement eligibility.

            Analyze the messages below and respond ONLY in this EXACT format — 4 lines, no extra text:
            team_voice:true
            problem_solver:false
            mentor:false
            decision_maker:true

            Replace true/false based on these criteria:
            - team_voice: at least 15 substantive technical or helpful messages (exclude greetings/ack only)
            - problem_solver: proposed concrete technical solutions in 3 or more separate discussions
            - mentor: showed a clear pattern of explaining concepts or guiding teammates (3+ instances)
            - decision_maker: made 5 or more clear technical decisions with stated reasoning

            Developer messages (last 30 days):
            """ + joined;
    }

    private String extractSnippet(String response, String key) {
        // Return a short snippet near the key mention as evidence
        int idx = response.toLowerCase().indexOf(key);
        if (idx < 0) return "";
        int start = Math.max(0, idx - 40);
        int end = Math.min(response.length(), idx + 80);
        return response.substring(start, end).trim();
    }
}
