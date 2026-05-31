package com.unityskill.achievement;

import com.unityskill.achievement.entity.TicketTagValue;
import com.unityskill.achievement.entity.UserAchievement;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementEvaluator {

    private final TicketRepository ticketRepository;
    private final TicketTagRepository ticketTagRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final WebSocketEventPublisher eventPublisher;

    /**
     * Evaluates all ticket-based and tag-based achievements for a user.
     * Called every time a ticket is closed.
     */
    @Transactional
    public void evaluateOnTicketClose(UUID userId) {
        List<Ticket> allClosed = ticketRepository.findAllByAssigneeId(userId)
            .stream()
            .filter(t -> t.getClosedAt() != null)
            .collect(Collectors.toList());

        evaluateTicketMetrics(userId, allClosed);
        evaluateTicketTags(userId);
    }

    /**
     * Evaluates chat-based achievements using AI analysis.
     * Called by the weekly scheduler with pre-analyzed results.
     */
    @Transactional
    public void awardChatAchievement(UUID userId, String achievementKey, Map<String, Object> evidence) {
        award(userId, null, achievementKey, evidence);
    }

    // ── Ticket-metric evaluation ──────────────────────────────────────────────

    private void evaluateTicketMetrics(UUID userId, List<Ticket> closedTickets) {
        if (closedTickets.isEmpty()) return;

        checkQuickCloser(userId, closedTickets);
        checkSprintMachine(userId, closedTickets);
        checkHeavyLifter(userId, closedTickets);
        checkConsistent(userId, closedTickets);
        checkSpeedrunner(userId, closedTickets);
        checkTaskMachine(userId, closedTickets);
    }

    private void checkQuickCloser(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "quick_closer")) return;
        // Find 3 tickets closed within any 24-hour rolling window
        List<Instant> closedTimes = closedTickets.stream()
            .map(Ticket::getClosedAt)
            .sorted()
            .collect(Collectors.toList());

        for (int i = 0; i <= closedTimes.size() - 3; i++) {
            Instant start = closedTimes.get(i);
            Instant end = closedTimes.get(i + 2);
            if (ChronoUnit.HOURS.between(start, end) <= 24) {
                award(userId, null, "quick_closer", Map.of(
                    "windowStart", start.toString(),
                    "windowEnd", end.toString(),
                    "ticketCount", 3
                ));
                return;
            }
        }
    }

    private void checkSprintMachine(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "sprint_machine")) return;
        // 10+ tickets closed in any calendar month
        Map<String, Long> byMonth = closedTickets.stream()
            .collect(Collectors.groupingBy(
                t -> {
                    LocalDate d = t.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate();
                    return d.getYear() + "-" + d.getMonthValue();
                },
                Collectors.counting()
            ));

        byMonth.entrySet().stream()
            .filter(e -> e.getValue() >= 10)
            .findFirst()
            .ifPresent(e -> award(userId, null, "sprint_machine", Map.of(
                "month", e.getKey(), "count", e.getValue()
            )));
    }

    private void checkHeavyLifter(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "heavy_lifter")) return;
        // 5+ tickets closed on the same calendar day
        Map<LocalDate, Long> byDay = closedTickets.stream()
            .collect(Collectors.groupingBy(
                t -> t.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                Collectors.counting()
            ));

        byDay.entrySet().stream()
            .filter(e -> e.getValue() >= 5)
            .findFirst()
            .ifPresent(e -> award(userId, null, "heavy_lifter", Map.of(
                "date", e.getKey().toString(), "count", e.getValue()
            )));
    }

    private void checkConsistent(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "consistent")) return;
        // At least 1 ticket/week for 4 consecutive ISO weeks
        Set<String> weeksWithActivity = closedTickets.stream()
            .map(t -> {
                LocalDate d = t.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate();
                int year = d.get(IsoFields.WEEK_BASED_YEAR);
                int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                return year + "-W" + week;
            })
            .collect(Collectors.toSet());

        // Sort weeks and look for 4 consecutive
        List<LocalDate> weekStarts = weeksWithActivity.stream()
            .map(w -> {
                String[] parts = w.split("-W");
                int year = Integer.parseInt(parts[0]);
                int week = Integer.parseInt(parts[1]);
                return LocalDate.of(year, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .with(java.time.DayOfWeek.MONDAY);
            })
            .sorted()
            .collect(Collectors.toList());

        int streak = 1;
        for (int i = 1; i < weekStarts.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(weekStarts.get(i - 1), weekStarts.get(i));
            if (daysBetween == 7) {
                streak++;
                if (streak >= 4) {
                    award(userId, null, "consistent", Map.of(
                        "streakWeeks", streak,
                        "streakEnd", weekStarts.get(i).toString()
                    ));
                    return;
                }
            } else {
                streak = 1;
            }
        }
    }

    private void checkSpeedrunner(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "speedrunner")) return;
        if (closedTickets.size() < 5) return;

        // Average time from createdAt to closedAt < 48h across ≥5 tickets
        OptionalDouble avgHours = closedTickets.stream()
            .mapToLong(t -> ChronoUnit.HOURS.between(t.getCreatedAt(), t.getClosedAt()))
            .filter(h -> h >= 0)
            .average();

        if (avgHours.isPresent() && avgHours.getAsDouble() < 48.0) {
            award(userId, null, "speedrunner", Map.of(
                "avgHours", Math.round(avgHours.getAsDouble()),
                "ticketCount", closedTickets.size()
            ));
        }
    }

    private void checkTaskMachine(UUID userId, List<Ticket> closedTickets) {
        if (alreadyEarned(userId, "task_machine")) return;
        if (closedTickets.size() >= 50) {
            award(userId, null, "task_machine", Map.of("totalClosed", closedTickets.size()));
        }
    }

    // ── Tag-based evaluation ──────────────────────────────────────────────────

    private void evaluateTicketTags(UUID userId) {
        checkTagAchievement(userId, "backend_dev",     TicketTagValue.BACKEND,       5);
        checkTagAchievement(userId, "bug_slayer",      TicketTagValue.BUG_FIX,       10);
        checkTagAchievement(userId, "frontend_dev",    TicketTagValue.FRONTEND,      5);
        checkTagAchievement(userId, "devops_engineer", TicketTagValue.DEVOPS,        5);
        checkTagAchievement(userId, "architect",       TicketTagValue.ARCHITECTURE,  3);
        checkTagAchievement(userId, "qa_champion",     TicketTagValue.TESTING,       8);
    }

    private void checkTagAchievement(UUID userId, String key, TicketTagValue tag, int threshold) {
        if (alreadyEarned(userId, key)) return;
        long count = ticketTagRepository.countClosedTaggedTicketsForUser(userId, tag.name());
        if (count >= threshold) {
            award(userId, null, key, Map.of("tag", tag.name(), "count", count));
        }
    }

    // ── Award ─────────────────────────────────────────────────────────────────

    private boolean alreadyEarned(UUID userId, String key) {
        return userAchievementRepository.existsByUserIdAndAchievementKey(userId, key);
    }

    private void award(UUID userId, UUID workspaceId, String key, Map<String, Object> evidence) {
        if (alreadyEarned(userId, key)) return;

        UserAchievement ua = UserAchievement.builder()
            .userId(userId)
            .workspaceId(workspaceId)
            .achievementKey(key)
            .evidenceSnapshot(evidence)
            .build();
        userAchievementRepository.save(ua);

        AchievementDefinition def = AchievementDefinitions.BY_KEY.get(key);
        if (def != null) {
            eventPublisher.publishNotification(userId, "ACHIEVEMENT_EARNED", Map.of(
                "key", key,
                "title", def.title(),
                "iconEmoji", def.iconEmoji(),
                "tier", def.tier().name()
            ));
        }
        log.info("Achievement awarded: {} → {}", userId, key);
    }
}
