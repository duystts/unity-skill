package com.unityskill.achievement.dto;

import com.unityskill.achievement.AchievementDefinition;
import com.unityskill.achievement.entity.AchievementSource;
import com.unityskill.achievement.entity.AchievementTier;

public record AchievementResponse(
    String key,
    String title,
    String description,
    String iconEmoji,
    AchievementTier tier,
    AchievementSource source
) {
    public static AchievementResponse from(AchievementDefinition def) {
        return new AchievementResponse(
            def.key(), def.title(), def.description(),
            def.iconEmoji(), def.tier(), def.source()
        );
    }
}
