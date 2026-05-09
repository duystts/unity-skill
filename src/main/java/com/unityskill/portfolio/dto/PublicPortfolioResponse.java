package com.unityskill.portfolio.dto;

import java.util.List;

public record PublicPortfolioResponse(
        String userId,
        String displayName,
        List<SkillGroup> skills,
        List<EndorsementInfo> endorsements,
        StreakInfo streak              // Story 7.5: was Object — now typed
) {
    public record SkillGroup(
            String skillCategory,
            int count,
            List<EvidenceItem> items
    ) {}

    public record EvidenceItem(
            String id,
            String skillCategory,
            String aiSummary,
            String publishedAt
    ) {}

    /** Story 7.4: endorsement info for public portfolio display */
    public record EndorsementInfo(
            String id,
            String evidenceId,
            String endorserId,
            String endorserName,
            String createdAt
    ) {}

    /** Story 7.5: streak badge data for public portfolio display. Null if no streak exists. */
    public record StreakInfo(int currentWeeks, int longestWeeks) {}
}
