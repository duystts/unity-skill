package com.unityskill.consent;

import com.unityskill.consent.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<ConsentRecord, UUID> {

    /** AC4: used by ConsentCheckFilter on every authenticated protected request. */
    boolean existsByUserId(UUID userId);

    /** AC3: used by ConsentService to implement idempotent consent recording. */
    Optional<ConsentRecord> findByUserId(UUID userId);
}
