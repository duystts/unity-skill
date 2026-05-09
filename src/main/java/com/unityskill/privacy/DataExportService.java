package com.unityskill.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.contribution.ContributionRepository;
import com.unityskill.contribution.SkillEvidenceRepository;
import com.unityskill.contribution.entity.ContributionEvent;
import com.unityskill.contribution.entity.SkillEvidence;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.portfolio.AwayPeriodRepository;
import com.unityskill.portfolio.ContributionStreakRepository;
import com.unityskill.portfolio.entity.AwayPeriod;
import com.unityskill.portfolio.entity.ContributionStreak;
import com.unityskill.privacy.entity.DataExport;
import com.unityskill.privacy.entity.ExportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportService {

    private final DataExportRepository dataExportRepository;
    private final ContributionRepository contributionRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final ContributionStreakRepository streakRepository;
    private final AwayPeriodRepository awayPeriodRepository;
    private final WebSocketEventPublisher wsPublisher;
    private final ObjectMapper objectMapper;

    /**
     * AC1: Asynchronously compiles all personal data for the user and stores it.
     * Sends a DATA_EXPORT_READY WebSocket notification when the export is ready.
     * Silently handles failures — deletes the IN_PROGRESS record on error so the
     * user can retry.
     *
     * @param userId the developer whose data to export
     */
    @Async("taskExecutor")
    public void generateExport(UUID userId) {
        // Create IN_PROGRESS record first — used by controller's duplicate check
        DataExport export = dataExportRepository.save(
                DataExport.builder().userId(userId).build());
        try {
            // AC2/3: collect all records belonging to this user across workspaces
            List<Map<String, Object>> events = contributionRepository.findAllByUserId(userId)
                    .stream().map(this::eventToMap).toList();
            List<Map<String, Object>> evidences = skillEvidenceRepository.findAllByUserId(userId)
                    .stream().map(this::evidenceToMap).toList();
            List<Map<String, Object>> streaks = streakRepository.findAllByUserId(userId)
                    .stream().map(this::streakToMap).toList();
            List<Map<String, Object>> awayPeriods = awayPeriodRepository.findAllByUserId(userId)
                    .stream().map(this::awayPeriodToMap).toList();

            // AC5: all timestamps via Instant.toString() → ISO 8601; all IDs as UUID strings
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId",             userId.toString());
            payload.put("exportedAt",          Instant.now().toString());
            payload.put("contributionEvents",  events);
            payload.put("skillEvidences",      evidences);
            payload.put("contributionStreaks", streaks);
            payload.put("awayPeriods",         awayPeriods);

            export.setExportJson(objectMapper.writeValueAsString(payload));
            export.setStatus(ExportStatus.READY);
            export.setCompletedAt(Instant.now());
            dataExportRepository.save(export);

            // AC1: notify developer that the download is ready
            wsPublisher.publishNotification(userId, "DATA_EXPORT_READY",
                    Map.of("exportId", export.getId().toString()));
            log.info("Data export completed for user {}: exportId={}", userId, export.getId());

        } catch (Exception e) {
            log.error("Data export generation failed for user {}: {}", userId, e.getMessage());
            // Clean up the IN_PROGRESS record so the user can retry
            dataExportRepository.delete(export);
        }
    }

    // ─── Private mapping helpers (AC5: ISO 8601 timestamps, UUID strings) ───────

    private Map<String, Object> eventToMap(ContributionEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          e.getId().toString());
        m.put("workspaceId", e.getWorkspaceId().toString());
        m.put("sourceType",  e.getSourceType().name());
        m.put("sourceRefId", e.getSourceRefId() != null ? e.getSourceRefId().toString() : null);
        m.put("skillSignals", e.getSkillSignals());
        m.put("processedAt", e.getProcessedAt().toString());
        m.put("createdAt",   e.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> evidenceToMap(SkillEvidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             e.getId().toString());
        m.put("workspaceId",    e.getWorkspaceId().toString());
        m.put("status",         e.getStatus().name());
        m.put("skillCategory",  e.getSkillCategory());
        m.put("aiSummary",      e.getAiSummary());
        m.put("developerNotes", e.getDeveloperNotes());
        m.put("sourceEvents",   e.getSourceEvents());
        m.put("isPublished",    e.isPublished());
        m.put("reviewedAt",  e.getReviewedAt()  != null ? e.getReviewedAt().toString()  : null);
        m.put("publishedAt", e.getPublishedAt() != null ? e.getPublishedAt().toString() : null);
        m.put("createdAt",   e.getCreatedAt().toString());
        m.put("updatedAt",   e.getUpdatedAt().toString());
        return m;
    }

    private Map<String, Object> streakToMap(ContributionStreak s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                s.getId().toString());
        m.put("workspaceId",       s.getWorkspaceId().toString());
        m.put("currentStreakWeeks", s.getCurrentStreakWeeks());
        m.put("longestStreakWeeks", s.getLongestStreakWeeks());
        m.put("lastActivityWeek",  s.getLastActivityWeek().toString()); // ISO LocalDate
        m.put("updatedAt",         s.getUpdatedAt().toString());
        return m;
    }

    private Map<String, Object> awayPeriodToMap(AwayPeriod a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          a.getId().toString());
        m.put("startDate",   a.getStartDate().toString());  // ISO LocalDate
        m.put("endDate",     a.getEndDate().toString());
        m.put("createdAt",   a.getCreatedAt().toString());
        return m;
    }
}
