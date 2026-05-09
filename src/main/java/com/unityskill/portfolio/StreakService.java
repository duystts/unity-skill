package com.unityskill.portfolio;

import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.entity.ContributionStreak;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {

    private final ContributionStreakRepository streakRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final AwayPeriodRepository awayPeriodRepository; // Story 7.6

    /**
     * Daily streak evaluation: runs at 01:00 every day.
     * <p>
     * AC1: if a developer has APPROVED evidence in the current ISO week and has not been counted
     * yet for this week, increment (or start) the streak.
     * <p>
     * AC2: if a streak record's last_activity_week is before the previous ISO week's Monday,
     * the developer missed a full calendar week → reset current_streak_weeks to 0.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void evaluateAllStreaks() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate currentWeekMonday  = today.with(DayOfWeek.MONDAY);
        LocalDate previousWeekMonday = currentWeekMonday.minusWeeks(1);
        Instant   currentWeekStart   = currentWeekMonday.atStartOfDay(ZoneOffset.UTC).toInstant();

        log.info("Running streak evaluation for week starting {}", currentWeekMonday);

        // Step 1: Increment streaks for developers active this week
        List<SkillEvidence> activeEvidence = skillEvidenceRepository
                .findAllByStatusAndReviewedAtGreaterThanEqual(EvidenceStatus.APPROVED, currentWeekStart);

        // Group by (userId, workspaceId) — deduplicate multiple evidence items in same week
        Set<UserWorkspacePair> activePairs = activeEvidence.stream()
                .map(e -> new UserWorkspacePair(e.getUserId(), e.getWorkspaceId()))
                .collect(Collectors.toSet());

        for (UserWorkspacePair pair : activePairs) {
            ContributionStreak streak = streakRepository
                    .findByUserIdAndWorkspaceId(pair.userId(), pair.workspaceId())
                    .orElseGet(() -> ContributionStreak.builder()
                            .userId(pair.userId())
                            .workspaceId(pair.workspaceId())
                            .currentStreakWeeks(0)
                            .longestStreakWeeks(0)
                            .lastActivityWeek(previousWeekMonday.minusWeeks(1)) // sentinel: triggers new streak
                            .build());

            if (!currentWeekMonday.equals(streak.getLastActivityWeek())) {
                // Not yet counted this week — determine if consecutive or gap
                if (previousWeekMonday.equals(streak.getLastActivityWeek())) {
                    // AC1: consecutive week → increment
                    streak.setCurrentStreakWeeks(streak.getCurrentStreakWeeks() + 1);
                } else {
                    // Gap detected (or first ever activity) → start new streak at 1
                    streak.setCurrentStreakWeeks(1);
                }
                streak.setLastActivityWeek(currentWeekMonday);
                streak.setLongestStreakWeeks(
                        Math.max(streak.getLongestStreakWeeks(), streak.getCurrentStreakWeeks()));
                streakRepository.save(streak);
                log.debug("Streak updated for user {} workspace {}: {}w",
                        pair.userId(), pair.workspaceId(), streak.getCurrentStreakWeeks());
            }
            // else: already counted this week — no-op (AC1: idempotent daily re-runs)
        }

        // Step 2: Reset streaks for developers who missed last week (AC2)
        // "Missed a full calendar week" = lastActivityWeek < previousWeekMonday
        List<ContributionStreak> staleStreaks = streakRepository
                .findAllByLastActivityWeekBefore(previousWeekMonday);

        for (ContributionStreak streak : staleStreaks) {
            if (streak.getCurrentStreakWeeks() > 0) {
                // Story 7.6: Check if the missed week was covered by an away period (AC2)
                // "Missed week" = previousWeekMonday (the Monday of the week before the current one).
                boolean protected_ = awayPeriodRepository
                        .existsByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                streak.getUserId(), previousWeekMonday, previousWeekMonday);
                if (protected_) {
                    // AC3: advance lastActivityWeek to previousWeekMonday so next contribution
                    // is treated as consecutive (streak resumes from where it left off)
                    streak.setLastActivityWeek(previousWeekMonday);
                    streakRepository.save(streak);
                    log.debug("Streak protected (away) for user {} workspace {}",
                            streak.getUserId(), streak.getWorkspaceId());
                } else {
                    // No away period covers the missed week — reset streak (AC2 inverse)
                    streak.setCurrentStreakWeeks(0);
                    streakRepository.save(streak);
                    log.debug("Streak reset for user {} workspace {}",
                            streak.getUserId(), streak.getWorkspaceId());
                }
            }
        }

        log.info("Streak evaluation complete: {} active pairs, {} stale resets",
                activePairs.size(), staleStreaks.size());
    }

    /** Internal: key for grouping evidence by developer+workspace. */
    private record UserWorkspacePair(UUID userId, UUID workspaceId) {}
}
