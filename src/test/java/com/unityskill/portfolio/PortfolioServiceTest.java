package com.unityskill.portfolio;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.dto.PublicPortfolioResponse;
import com.unityskill.portfolio.entity.ContributionStreak;
import com.unityskill.portfolio.entity.Endorsement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock UserRepository userRepository;
    @Mock EndorsementRepository endorsementRepository;   // Story 7.4
    @Mock ContributionStreakRepository streakRepository; // Story 7.5
    @InjectMocks PortfolioService portfolioService;

    @Test
    void getPublicPortfolio_publishedItemsGroupedByCategory() {
        // AC1: only is_published=true items, grouped by skill_category, sorted by publishedAt desc
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        User user = User.builder()
                .id(userId).email("alice@example.com")
                .displayName("Alice").build();

        SkillEvidence ev1 = SkillEvidence.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary 1.").isPublished(true)
                .publishedAt(Instant.parse("2026-04-20T00:00:00Z")).build();

        SkillEvidence ev2 = SkillEvidence.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary 2.").isPublished(true)
                .publishedAt(Instant.parse("2026-04-10T00:00:00Z")).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId))
                .thenReturn(List.of(ev1, ev2));
        // Story 7.4 regression fix: stub endorsementRepository to avoid NPE
        when(endorsementRepository.findAllByEvidenceIdIn(any())).thenReturn(List.of());
        // Story 7.5 regression fix: stub streakRepository to avoid NPE (List<T> defaults to null)
        when(streakRepository.findAllByUserId(any())).thenReturn(List.of());

        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);

        assertThat(result.userId()).isEqualTo(userId.toString());
        assertThat(result.displayName()).isEqualTo("Alice");
        assertThat(result.skills()).hasSize(1);
        assertThat(result.skills().get(0).skillCategory()).isEqualTo("Backend Development");
        assertThat(result.skills().get(0).count()).isEqualTo(2);
        // Items ordered publishedAt desc: ev1 (Apr 20) before ev2 (Apr 10)
        assertThat(result.skills().get(0).items().get(0).aiSummary()).isEqualTo("Summary 1.");
        assertThat(result.skills().get(0).items().get(1).aiSummary()).isEqualTo("Summary 2.");
        assertThat(result.endorsements()).isEmpty();
        assertThat(result.streak()).isNull();
    }

    @Test
    void getPublicPortfolio_noPublishedEvidence_returnsEmptySkills() {
        // AC2: user exists, no published evidence → empty skills (publishedIds.isEmpty() skips endorsementRepo)
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId).email("bob@example.com")
                .displayName("Bob").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId))
                .thenReturn(List.of());
        // Story 7.5 regression fix: stub streakRepository to avoid NPE
        when(streakRepository.findAllByUserId(any())).thenReturn(List.of());

        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);

        assertThat(result.userId()).isEqualTo(userId.toString());
        assertThat(result.displayName()).isEqualTo("Bob");
        assertThat(result.skills()).isEmpty();
        assertThat(result.endorsements()).isEmpty();
        assertThat(result.streak()).isNull();
    }

    @Test
    void getPublicPortfolio_approvedButUnpublished_notExposedInPublicPortfolio() {
        // AC2 (Story 9.3): APPROVED evidence with isPublished=false must NOT appear in public portfolio
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId).email("alice@example.com").displayName("Alice").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // Repository only returns is_published=true records; approved-but-unpublished is excluded
        when(skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId))
                .thenReturn(List.of());
        when(streakRepository.findAllByUserId(any())).thenReturn(List.of());

        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);

        assertThat(result.skills()).isEmpty();  // AC2: no evidence leaked even when APPROVED
    }

    @Test
    void getPublicPortfolio_unknownUser_throwsUnauthorizedAccess() {
        // AC1: unknown userId → 403 (user enumeration prevention)
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPublicPortfolio(unknownId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getPublicPortfolio_withEndorsements_populatesEndorsementsInResponse() {
        // Story 7.4 AC4: endorsements appear in public portfolio response with endorser name
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID evidenceId  = UUID.randomUUID();
        UUID endorserId  = UUID.randomUUID();

        User user = User.builder().id(userId).email("alice@example.com").displayName("Alice").build();
        User endorserUser = User.builder().id(endorserId).email("bob@example.com").displayName("Bob").build();

        SkillEvidence ev = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(workspaceId)
                .status(EvidenceStatus.APPROVED).skillCategory("Backend Development")
                .aiSummary("Summary.").isPublished(true)
                .publishedAt(Instant.parse("2026-04-20T00:00:00Z")).build();

        Endorsement endorsement = Endorsement.builder()
                .id(UUID.randomUUID())
                .evidenceId(evidenceId).endorserId(endorserId)
                .workspaceId(workspaceId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId)).thenReturn(List.of(ev));
        when(endorsementRepository.findAllByEvidenceIdIn(List.of(evidenceId))).thenReturn(List.of(endorsement));
        when(userRepository.findAllById(List.of(endorserId))).thenReturn(List.of(endorserUser));
        // Story 7.5 regression fix: stub streakRepository to avoid NPE
        when(streakRepository.findAllByUserId(any())).thenReturn(List.of());

        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);

        assertThat(result.endorsements()).hasSize(1);
        assertThat(result.endorsements().get(0).endorserName()).isEqualTo("Bob");
        assertThat(result.endorsements().get(0).evidenceId()).isEqualTo(evidenceId.toString());
    }

    @Test
    void getPublicPortfolio_withStreak_returnsStreakBadgeData() {
        // AC4: streak appears in public portfolio response
        UUID userId      = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        User user = User.builder().id(userId).email("alice@example.com").displayName("Alice").build();

        ContributionStreak streak = ContributionStreak.builder()
                .id(UUID.randomUUID()).userId(userId).workspaceId(workspaceId)
                .currentStreakWeeks(5).longestStreakWeeks(8)
                .lastActivityWeek(java.time.LocalDate.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId)).thenReturn(List.of());
        // publishedIds is empty → endorsementRepository.findAllByEvidenceIdIn() NOT called (short-circuit)
        when(streakRepository.findAllByUserId(userId)).thenReturn(List.of(streak));

        PublicPortfolioResponse result = portfolioService.getPublicPortfolio(userId);

        assertThat(result.streak()).isNotNull();
        assertThat(result.streak().currentWeeks()).isEqualTo(5);
        assertThat(result.streak().longestWeeks()).isEqualTo(8);
    }
}
