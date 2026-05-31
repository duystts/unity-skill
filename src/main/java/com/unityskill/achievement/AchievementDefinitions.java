package com.unityskill.achievement;

import com.unityskill.achievement.entity.AchievementSource;
import com.unityskill.achievement.entity.AchievementTier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AchievementDefinitions {

    public static final List<AchievementDefinition> ALL = List.of(
        // ── Ticket-metric ─────────────────────────────────────────────────────
        new AchievementDefinition(
            "quick_closer", "Quick Closer",
            "Closed 3 tickets within a 24-hour window",
            "⚡", AchievementTier.BRONZE, AchievementSource.TICKET_METRIC),
        new AchievementDefinition(
            "sprint_machine", "Sprint Machine",
            "Closed 10 tickets in a single month",
            "🚀", AchievementTier.SILVER, AchievementSource.TICKET_METRIC),
        new AchievementDefinition(
            "heavy_lifter", "Heavy Lifter",
            "Closed 5 tickets in a single day",
            "💪", AchievementTier.SILVER, AchievementSource.TICKET_METRIC),
        new AchievementDefinition(
            "consistent", "Consistent",
            "Closed at least 1 ticket per week for 4 consecutive weeks",
            "📅", AchievementTier.BRONZE, AchievementSource.TICKET_METRIC),
        new AchievementDefinition(
            "speedrunner", "Speedrunner",
            "Average time-to-close under 48h across 5 or more tickets",
            "🏎️", AchievementTier.GOLD, AchievementSource.TICKET_METRIC),
        new AchievementDefinition(
            "task_machine", "Task Machine",
            "Closed 50 tickets total",
            "🏆", AchievementTier.GOLD, AchievementSource.TICKET_METRIC),

        // ── Ticket-tag ────────────────────────────────────────────────────────
        new AchievementDefinition(
            "backend_dev", "Backend Dev",
            "Closed 5 Backend-tagged tickets",
            "🖥️", AchievementTier.BRONZE, AchievementSource.TICKET_TAG),
        new AchievementDefinition(
            "bug_slayer", "Bug Slayer",
            "Closed 10 Bug Fix-tagged tickets",
            "🐛", AchievementTier.SILVER, AchievementSource.TICKET_TAG),
        new AchievementDefinition(
            "frontend_dev", "Frontend Dev",
            "Closed 5 Frontend-tagged tickets",
            "🎨", AchievementTier.BRONZE, AchievementSource.TICKET_TAG),
        new AchievementDefinition(
            "devops_engineer", "DevOps Engineer",
            "Closed 5 DevOps-tagged tickets",
            "⚙️", AchievementTier.BRONZE, AchievementSource.TICKET_TAG),
        new AchievementDefinition(
            "architect", "Architect",
            "Closed 3 Architecture-tagged tickets",
            "🏗️", AchievementTier.SILVER, AchievementSource.TICKET_TAG),
        new AchievementDefinition(
            "qa_champion", "QA Champion",
            "Closed 8 Testing-tagged tickets",
            "✅", AchievementTier.BRONZE, AchievementSource.TICKET_TAG),

        // ── Chat-AI ───────────────────────────────────────────────────────────
        new AchievementDefinition(
            "team_voice", "Team Voice",
            "At least 15 substantive technical messages in the last 30 days",
            "💬", AchievementTier.BRONZE, AchievementSource.CHAT_AI),
        new AchievementDefinition(
            "problem_solver", "Problem Solver",
            "Proposed technical solutions in 3 or more discussions",
            "🔧", AchievementTier.SILVER, AchievementSource.CHAT_AI),
        new AchievementDefinition(
            "mentor", "Mentor",
            "Showed a pattern of explaining and guiding teammates",
            "🎓", AchievementTier.SILVER, AchievementSource.CHAT_AI),
        new AchievementDefinition(
            "decision_maker", "Decision Maker",
            "Made 5 or more clear technical decisions in discussion",
            "🎯", AchievementTier.GOLD, AchievementSource.CHAT_AI)
    );

    public static final Map<String, AchievementDefinition> BY_KEY =
        ALL.stream().collect(Collectors.toMap(AchievementDefinition::key, Function.identity()));

    private AchievementDefinitions() {}
}
