package com.unityskill.portfolio;

import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.entity.ContributionStreak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock ContributionStreakRepository streakRepository;
    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock AwayPeriodRepository awayPeriodRepository; // Story 7.6: boolean default = false → existing tests unaffected
    @InjectMocks StreakService streakService;

    private LocalDate currentWeekMonday() {
        return LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
    }

    private SkillEvidence approvedEvidence(UUID userId, UUID workspaceId) {
        return SkillEvidence.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED)
                .skillCategory("Backend Development")
                .aiSummary("Summary.").isPublished(true)
                .reviewedAt(Instant.now())
                .build();
    }

    @Test
    void evaluateAllStreaks_firstActivityThisWeek_createsStreakAtOne() {
        // AC1: new developer with approved evidence this week → streak starts at 1
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of(approvedEvidence(userId, workspaceId)));
        when(streakRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.empty());   // no existing streak
        when(streakRepository.findAllByLastActivityWeekBefore(any()))
                .thenReturn(List.of());
        when(streakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streakService.evaluateAllStreaks();

        ArgumentCaptor<ContributionStreak> captor = ArgumentCaptor.forClass(ContributionStreak.class);
        verify(streakRepository).save(captor.capture());
        ContributionStreak saved = captor.getValue();
        assertThat(saved.getCurrentStreakWeeks()).isEqualTo(1);
        assertThat(saved.getLongestStreakWeeks()).isEqualTo(1);
        assertThat(saved.getLastActivityWeek()).isEqualTo(currentWeekMonday());
    }

    @Test
    void evaluateAllStreaks_consecutiveWeekActivity_incrementsStreak() {
        // AC1: developer had activity last week → increment streak to 2
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        LocalDate currentWeekMonday  = currentWeekMonday();
        LocalDate previousWeekMonday = currentWeekMonday.minusWeeks(1);

        ContributionStreak existing = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(1).longestStreakWeeks(1)
                .lastActivityWeek(previousWeekMonday)  // was active last week
                .build();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of(approvedEvidence(userId, workspaceId)));
        when(streakRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(existing));
        when(streakRepository.findAllByLastActivityWeekBefore(any()))
                .thenReturn(List.of());
        when(streakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streakService.evaluateAllStreaks();

        ArgumentCaptor<ContributionStreak> captor = ArgumentCaptor.forClass(ContributionStreak.class);
        verify(streakRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentStreakWeeks()).isEqualTo(2);
        assertThat(captor.getValue().getLongestStreakWeeks()).isEqualTo(2);
    }

    @Test
    void evaluateAllStreaks_alreadyCountedThisWeek_doesNotDoubleCount() {
        // AC1: idempotent — if streak.lastActivityWeek == currentWeekMonday, do nothing
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        LocalDate currentWeekMonday = currentWeekMonday();

        ContributionStreak existing = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(3).longestStreakWeeks(5)
                .lastActivityWeek(currentWeekMonday)  // already counted this week
                .build();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of(approvedEvidence(userId, workspaceId)));
        when(streakRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(existing));
        when(streakRepository.findAllByLastActivityWeekBefore(any()))
                .thenReturn(List.of());

        streakService.evaluateAllStreaks();

        verify(streakRepository, never()).save(any());
    }

    @Test
    void evaluateAllStreaks_missedPreviousWeek_resetsCurrentStreakToZero() {
        // AC2: no activity this week; lastActivityWeek < previousWeekMonday → reset
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        LocalDate previousWeekMonday = currentWeekMonday().minusWeeks(1);

        ContributionStreak stale = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(4).longestStreakWeeks(4)
                .lastActivityWeek(previousWeekMonday.minusWeeks(1))  // 2 weeks ago
                .build();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of());  // no activity this week
        when(streakRepository.findAllByLastActivityWeekBefore(previousWeekMonday))
                .thenReturn(List.of(stale));
        when(streakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streakService.evaluateAllStreaks();

        ArgumentCaptor<ContributionStreak> captor = ArgumentCaptor.forClass(ContributionStreak.class);
        verify(streakRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentStreakWeeks()).isEqualTo(0);
        // longestStreakWeeks should not change on reset
        assertThat(captor.getValue().getLongestStreakWeeks()).isEqualTo(4);
    }

    @Test
    void evaluateAllStreaks_awayPeriodCoversGapWeek_doesNotResetStreak() {
        // AC2: stale streak but missed week is covered by away period → no reset, lastActivityWeek advances
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        LocalDate currentWeekMonday  = currentWeekMonday();
        LocalDate previousWeekMonday = currentWeekMonday.minusWeeks(1);

        ContributionStreak stale = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(5).longestStreakWeeks(5)
                .lastActivityWeek(previousWeekMonday.minusWeeks(1))  // 2 weeks ago — stale
                .build();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of());  // no activity this week
        when(streakRepository.findAllByLastActivityWeekBefore(previousWeekMonday))
                .thenReturn(List.of(stale));
        // Away period covers previousWeekMonday → streak is protected
        when(awayPeriodRepository.existsByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, previousWeekMonday, previousWeekMonday))
                .thenReturn(true);
        when(streakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streakService.evaluateAllStreaks();

        ArgumentCaptor<ContributionStreak> captor = ArgumentCaptor.forClass(ContributionStreak.class);
        verify(streakRepository).save(captor.capture());
        // currentStreakWeeks NOT reset — streak protected
        assertThat(captor.getValue().getCurrentStreakWeeks()).isEqualTo(5);
        // lastActivityWeek advanced to previousWeekMonday (enables consecutive check next week — AC3)
        assertThat(captor.getValue().getLastActivityWeek()).isEqualTo(previousWeekMonday);
    }

    @Test
    void evaluateAllStreaks_awayPeriodEnds_streakResumesFromWhereLeftOff() {
        // AC3: after away period protection, developer contributes next week → consecutive increment
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        LocalDate currentWeekMonday  = currentWeekMonday();
        LocalDate previousWeekMonday = currentWeekMonday.minusWeeks(1);

        // After protection last week: lastActivityWeek == previousWeekMonday (advanced during away period)
        ContributionStreak streak = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(5).longestStreakWeeks(5)
                .lastActivityWeek(previousWeekMonday)  // advanced during away protection
                .build();

        when(skillEvidenceRepository.findAllByStatusAndReviewedAtGreaterThanEqual(
                eq(EvidenceStatus.APPROVED), any()))
                .thenReturn(List.of(approvedEvidence(userId, workspaceId)));  // active this week
        when(streakRepository.findByUserIdAndWorkspaceId(userId, workspaceId))
                .thenReturn(Optional.of(streak));
        when(streakRepository.findAllByLastActivityWeekBefore(any()))
                .thenReturn(List.of());  // not stale (lastActivityWeek == previousWeekMonday)
        when(streakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        streakService.evaluateAllStreaks();

        ArgumentCaptor<ContributionStreak> captor = ArgumentCaptor.forClass(ContributionStreak.class);
        verify(streakRepository).save(captor.capture());
        // AC3: streak incremented from 5 → 6 (consecutive because lastActivityWeek == previousWeekMonday)
        assertThat(captor.getValue().getCurrentStreakWeeks()).isEqualTo(6);
        assertThat(captor.getValue().getLongestStreakWeeks()).isEqualTo(6);
        assertThat(captor.getValue().getLastActivityWeek()).isEqualTo(currentWeekMonday);
    }
}
