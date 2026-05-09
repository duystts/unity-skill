package com.unityskill.privacy;

import com.unityskill.contribution.ContributionRepository;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.AwayPeriodRepository;
import com.unityskill.portfolio.ContributionStreakRepository;
import com.unityskill.portfolio.EndorsementRepository;
import com.unityskill.tracking.TrackingPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataDeletionService {

    private final ContributionRepository contributionRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final ContributionStreakRepository streakRepository;
    private final AwayPeriodRepository awayPeriodRepository;
    private final EndorsementRepository endorsementRepository;
    private final TrackingPermissionRepository trackingPermissionRepository;

    /**
     * AC1/3/4/5: Permanently deletes all contribution data for the user.
     * Synchronized within a transaction — all-or-nothing atomicity.
     * Idempotent: repeated calls on already-deleted user return all zeros (AC5).
     *
     * Deletion order (enforces FK constraint safety):
     * 1. Endorsements ON user's evidence (evidenceId FK → skill_evidences.id)
     * 2. Endorsements GIVEN BY user (endorserId — on others' evidence)
     * 3. SkillEvidences (userId FK)
     * 4. ContributionEvents (userId FK)
     * 5. ContributionStreaks (userId FK)
     * 6. AwayPeriods (userId FK)
     * 7. TrackingPermissions (userId FK)
     *
     * NOT deleted: users, workspace_members, consent_records — AC3 restriction.
     *
     * @param userId the developer whose data to permanently delete
     * @return counts of deleted records per table
     */
    @Transactional
    public Map<String, Integer> deleteAllContributionData(UUID userId) {
        // Step 1: Collect evidence IDs before deletion (needed for endorsement FK safety)
        List<UUID> evidenceIds = skillEvidenceRepository.findAllByUserId(userId)
                .stream().map(SkillEvidence::getId).toList();

        // Step 2: Delete endorsements ON user's evidence (must precede SkillEvidence deletion)
        int endorsementsOnEvidence = 0;
        if (!evidenceIds.isEmpty()) {
            // Guard: JPQL IN clause must not receive an empty collection
            endorsementsOnEvidence = endorsementRepository.deleteAllByEvidenceIdIn(evidenceIds);
        }

        // Step 3: Delete endorsements GIVEN BY user (to others' evidence)
        int endorsementsGiven = endorsementRepository.deleteAllByEndorserId(userId);
        int totalEndorsements = endorsementsOnEvidence + endorsementsGiven;

        // Step 4: Delete skill evidences
        int skillEvidenceCount = skillEvidenceRepository.deleteAllByUserId(userId);

        // Step 5: Delete contribution events
        int contributionEventCount = contributionRepository.deleteAllByUserId(userId);

        // Step 6: Delete contribution streaks
        int streakCount = streakRepository.deleteAllByUserId(userId);

        // Step 7: Delete away periods
        int awayPeriodCount = awayPeriodRepository.deleteAllByUserId(userId);

        // Step 8: Delete tracking permissions
        int trackingPermissionCount = trackingPermissionRepository.deleteAllByUserId(userId);

        log.info("Data deletion completed for user {}: events={}, evidences={}, streaks={}, awayPeriods={}, endorsements={}, trackingPermissions={}",
                userId, contributionEventCount, skillEvidenceCount, streakCount,
                awayPeriodCount, totalEndorsements, trackingPermissionCount);

        // AC4: return counts by table
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("contributionEvents",  contributionEventCount);
        counts.put("skillEvidences",      skillEvidenceCount);
        counts.put("contributionStreaks",  streakCount);
        counts.put("awayPeriods",         awayPeriodCount);
        counts.put("endorsements",        totalEndorsements);
        counts.put("trackingPermissions", trackingPermissionCount);
        return counts;
    }
}
