package com.unityskill.portfolio;

import com.unityskill.portfolio.entity.AwayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AwayPeriodRepository extends JpaRepository<AwayPeriod, UUID> {

    /** Used by AwayPeriodService: fetch all away periods for the authenticated user. */
    List<AwayPeriod> findAllByUserId(UUID userId);

    /**
     * Used by StreakService: check if a given week is covered by any away period for a user.
     * "Covered" means: startDate <= weekMonday <= endDate (i.e., weekMonday falls within the period).
     *
     * Query semantics: WHERE user_id = ? AND start_date <= ? AND end_date >= ?
     * The same weekMonday value is passed for both bound parameters.
     */
    boolean existsByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID userId, LocalDate startDateBound, LocalDate endDateBound);

    /**
     * Story 9.5: permanently delete all away periods for a user.
     * Returns count of deleted rows for the deletion report.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AwayPeriod a WHERE a.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
