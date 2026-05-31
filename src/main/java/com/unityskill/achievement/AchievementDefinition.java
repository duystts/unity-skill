package com.unityskill.achievement;

import com.unityskill.achievement.entity.AchievementSource;
import com.unityskill.achievement.entity.AchievementTier;

public record AchievementDefinition(
    String key,
    String title,
    String description,
    String iconEmoji,
    AchievementTier tier,
    AchievementSource source
) {}
