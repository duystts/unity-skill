package com.unityskill.portfolio;

import com.unityskill.portfolio.entity.ContributionStreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionStreakRepository extends JpaRepository<ContributionStreak, UUID> {

    /** Used by SkillEvidenceService: per-workspace streak for the skill profile endpoint. */
    Optional<ContributionStreak> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    /** Used by PortfolioService: all workspace streaks to pick the best for the public portfolio. */
    List<ContributionStreak> findAllByUserId(UUID userId);

    /** Used by StreakService: find stale streaks to reset (missed a full week). */
    List<ContributionStreak> findAllByLastActivityWeekBefore(LocalDate date);

    /**
     * Story 9.5: permanently delete all contribution streaks for a user.
     * Returns count of deleted rows for the deletion report.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ContributionStreak s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
