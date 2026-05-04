package com.unityskill.portfolio;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.portfolio.dto.PublicPortfolioResponse;
import com.unityskill.portfolio.entity.ContributionStreak;
import com.unityskill.portfolio.entity.Endorsement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final SkillEvidenceRepository skillEvidenceRepository;
    private final UserRepository userRepository;
    private final EndorsementRepository endorsementRepository;   // Story 7.4
    private final ContributionStreakRepository streakRepository; // Story 7.5

    /**
     * Returns the public portfolio for a developer.
     * AC1 (7.3): Only is_published=true evidence, grouped by skill_category.
     * AC2 (7.3): Empty skills/endorsements/streak for users with no published evidence.
     * AC4 (7.4): Endorsements batch-fetched and populated in response.
     * Throws UnauthorizedAccessException (→ 403) for unknown userIds to prevent user enumeration.
     *
     * @param userId the developer whose portfolio to fetch
     */
    public PublicPortfolioResponse getPublicPortfolio(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedAccessException("User not found"));

        List<SkillEvidence> published =
                skillEvidenceRepository.findAllByUserIdAndIsPublishedTrue(userId);

        // Group by skill_category; sort by publishedAt descending within each group
        Map<String, List<SkillEvidence>> grouped = published.stream()
                .sorted(Comparator.comparing(SkillEvidence::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.groupingBy(
                        SkillEvidence::getSkillCategory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PublicPortfolioResponse.SkillGroup> skills = grouped.entrySet().stream()
                .map(entry -> new PublicPortfolioResponse.SkillGroup(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .map(e -> new PublicPortfolioResponse.EvidenceItem(
                                        e.getId().toString(),
                                        e.getSkillCategory(),
                                        e.getAiSummary(),
                                        e.getPublishedAt() != null ? e.getPublishedAt().toString() : null
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        // Story 7.4 AC4: batch-fetch all endorsements for this user's published evidence (2 queries total)
        List<UUID> publishedIds = published.stream()
                .map(SkillEvidence::getId)
                .collect(Collectors.toList());

        List<Endorsement> allEndorsements = publishedIds.isEmpty()
                ? List.of()
                : endorsementRepository.findAllByEvidenceIdIn(publishedIds);

        List<UUID> endorserIds = allEndorsements.stream()
                .map(Endorsement::getEndorserId)
                .distinct()
                .collect(Collectors.toList());
        Map<UUID, String> endorserNames = userRepository.findAllById(endorserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<PublicPortfolioResponse.EndorsementInfo> endorsements = allEndorsements.stream()
                .map(e -> new PublicPortfolioResponse.EndorsementInfo(
                        e.getId().toString(),
                        e.getEvidenceId().toString(),
                        e.getEndorserId().toString(),
                        endorserNames.getOrDefault(e.getEndorserId(), "Unknown"),
                        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());

        // Story 7.5: pick the best current streak across all workspaces the user is in
        PublicPortfolioResponse.StreakInfo streak = streakRepository.findAllByUserId(userId).stream()
                .max(Comparator.comparingInt(ContributionStreak::getCurrentStreakWeeks))
                .map(s -> new PublicPortfolioResponse.StreakInfo(
                        s.getCurrentStreakWeeks(),
                        s.getLongestStreakWeeks()))
                .orElse(null);

        return new PublicPortfolioResponse(
                userId.toString(),
                user.getDisplayName(),
                skills,
                endorsements,
                streak
        );
    }
}
