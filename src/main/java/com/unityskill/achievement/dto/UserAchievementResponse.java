package com.unityskill.achievement.dto;

import com.unityskill.achievement.AchievementDefinition;
import com.unityskill.achievement.AchievementDefinitions;
import com.unityskill.achievement.entity.AchievementSource;
import com.unityskill.achievement.entity.AchievementTier;
import com.unityskill.achievement.entity.UserAchievement;

import java.time.Instant;
import java.util.Map;

public record UserAchievementResponse(
    String key,
    String title,
    String description,
    String iconEmoji,
    AchievementTier tier,
    AchievementSource source,
    Instant earnedAt,
    Map<String, Object> evidenceSnapshot
) {
    public static UserAchievementResponse from(UserAchievement ua) {
        AchievementDefinition def = AchievementDefinitions.BY_KEY.get(ua.getAchievementKey());
        if (def == null) return null;
        return new UserAchievementResponse(
            def.key(), def.title(), def.description(),
            def.iconEmoji(), def.tier(), def.source(),
            ua.getEarnedAt(), ua.getEvidenceSnapshot()
        );
    }
}
