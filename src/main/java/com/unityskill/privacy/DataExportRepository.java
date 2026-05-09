package com.unityskill.privacy;

import com.unityskill.privacy.entity.DataExport;
import com.unityskill.privacy.entity.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DataExportRepository extends JpaRepository<DataExport, UUID> {

    /**
     * AC4: check if an export is already in progress for this user.
     * Used by controller to return 202 ACCEPTED and skip duplicate job.
     */
    boolean existsByUserIdAndStatus(UUID userId, ExportStatus status);

    /**
     * AC2: fetch the most recent completed export for download.
     * Returns the latest READY record (user may have triggered multiple exports).
     */
    Optional<DataExport> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, ExportStatus status);
}
