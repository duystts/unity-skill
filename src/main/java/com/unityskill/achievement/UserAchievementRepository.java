package com.unityskill.achievement;

import com.unityskill.achievement.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {

    boolean existsByUserIdAndAchievementKey(UUID userId, String achievementKey);

    List<UserAchievement> findAllByUserIdOrderByEarnedAtDesc(UUID userId);
}
