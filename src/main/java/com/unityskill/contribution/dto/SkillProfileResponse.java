package com.unityskill.contribution.dto;

import java.util.List;

public record SkillProfileResponse(
        int totalApproved,
        List<SkillCategoryGroup> categories,
        StreakInfo streak          // Story 7.5: null if no streak record exists yet
) {

    public record SkillCategoryGroup(
            String skillCategory,
            int count,
            List<SkillEvidenceResponse> items
    ) {}

    /** Story 7.5: streak summary for the private skill profile response. */
    public record StreakInfo(int currentWeeks, int longestWeeks) {}
}
