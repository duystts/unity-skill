package com.unityskill.portfolio;

import com.unityskill.portfolio.entity.Endorsement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EndorsementRepository extends JpaRepository<Endorsement, UUID> {

    boolean existsByEvidenceIdAndEndorserId(UUID evidenceId, UUID endorserId);

    /** Batch fetch: all endorsements across multiple evidence items in a single query. */
    List<Endorsement> findAllByEvidenceIdIn(Collection<UUID> evidenceIds);

    /**
     * Story 9.5: delete endorsements ON user's own evidence (FK safety — must run
     * BEFORE deleteAllByUserId on SkillEvidenceRepository).
     * evidenceIds MUST be non-empty; caller guards against empty list.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Endorsement e WHERE e.evidenceId IN :evidenceIds")
    int deleteAllByEvidenceIdIn(@Param("evidenceIds") Collection<UUID> evidenceIds);

    /**
     * Story 9.5: delete endorsements GIVEN BY this user (on others' evidence).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Endorsement e WHERE e.endorserId = :endorserId")
    int deleteAllByEndorserId(@Param("endorserId") UUID endorserId);
}
