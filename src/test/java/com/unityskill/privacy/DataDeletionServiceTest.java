package com.unityskill.privacy;

import com.unityskill.contribution.ContributionRepository;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.EvidenceStatus;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.AwayPeriodRepository;
import com.unityskill.portfolio.ContributionStreakRepository;
import com.unityskill.portfolio.EndorsementRepository;
import com.unityskill.tracking.TrackingPermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataDeletionServiceTest {

    @Mock ContributionRepository contributionRepository;
    @Mock SkillEvidenceRepository skillEvidenceRepository;
    @Mock ContributionStreakRepository streakRepository;
    @Mock AwayPeriodRepository awayPeriodRepository;
    @Mock EndorsementRepository endorsementRepository;
    @Mock TrackingPermissionRepository trackingPermissionRepository;
    @InjectMocks DataDeletionService dataDeletionService;

    @Test
    void deleteAllContributionData_userWithData_returnsCorrectCounts() {
        // AC1/4: happy path — all tables deleted, counts returned correctly
        UUID userId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        SkillEvidence evidence = SkillEvidence.builder()
                .id(evidenceId).userId(userId).workspaceId(UUID.randomUUID())
                .skillCategory("Backend").aiSummary("summary")
                .status(EvidenceStatus.APPROVED).build();
        when(skillEvidenceRepository.findAllByUserId(userId)).thenReturn(List.of(evidence));
        when(endorsementRepository.deleteAllByEvidenceIdIn(List.of(evidenceId))).thenReturn(2);
        when(endorsementRepository.deleteAllByEndorserId(userId)).thenReturn(1);
        when(skillEvidenceRepository.deleteAllByUserId(userId)).thenReturn(1);
        when(contributionRepository.deleteAllByUserId(userId)).thenReturn(5);
        when(streakRepository.deleteAllByUserId(userId)).thenReturn(1);
        when(awayPeriodRepository.deleteAllByUserId(userId)).thenReturn(2);
        when(trackingPermissionRepository.deleteAllByUserId(userId)).thenReturn(3);

        Map<String, Integer> counts = dataDeletionService.deleteAllContributionData(userId);

        assertThat(counts.get("contributionEvents")).isEqualTo(5);
        assertThat(counts.get("skillEvidences")).isEqualTo(1);
        assertThat(counts.get("contributionStreaks")).isEqualTo(1);
        assertThat(counts.get("awayPeriods")).isEqualTo(2);
        assertThat(counts.get("endorsements")).isEqualTo(3);  // 2 on evidence + 1 given
        assertThat(counts.get("trackingPermissions")).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteAllContributionData_noSkillEvidences_skipsEvidenceEndorsementDelete() {
        // Guard: JPQL IN clause must NOT receive empty collection — service skips call
        UUID userId = UUID.randomUUID();

        when(skillEvidenceRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(endorsementRepository.deleteAllByEndorserId(userId)).thenReturn(0);
        when(skillEvidenceRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(contributionRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(streakRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(awayPeriodRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(trackingPermissionRepository.deleteAllByUserId(userId)).thenReturn(0);

        Map<String, Integer> counts = dataDeletionService.deleteAllContributionData(userId);

        // deleteAllByEvidenceIdIn MUST NOT be called when evidence list is empty
        verify(endorsementRepository, never()).deleteAllByEvidenceIdIn(any(Collection.class));
        assertThat(counts.get("endorsements")).isEqualTo(0);
    }

    @Test
    void deleteAllContributionData_alreadyDeleted_returnsAllZeroCounts() {
        // AC5: idempotent — second call after deletion returns zeros, not an error
        UUID userId = UUID.randomUUID();

        when(skillEvidenceRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(endorsementRepository.deleteAllByEndorserId(userId)).thenReturn(0);
        when(skillEvidenceRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(contributionRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(streakRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(awayPeriodRepository.deleteAllByUserId(userId)).thenReturn(0);
        when(trackingPermissionRepository.deleteAllByUserId(userId)).thenReturn(0);

        Map<String, Integer> counts = dataDeletionService.deleteAllContributionData(userId);

        assertThat(counts.values()).allMatch(v -> v == 0);  // all zeros, no exception
    }
}
