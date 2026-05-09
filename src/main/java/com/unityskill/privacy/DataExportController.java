package com.unityskill.privacy;

import com.unityskill.common.exception.BadRequestException;
import com.unityskill.privacy.entity.DataExport;
import com.unityskill.privacy.entity.ExportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportRepository dataExportRepository;
    private final DataExportService dataExportService;

    /**
     * POST /api/v1/users/me/data-export
     * AC1: trigger async export; returns 202 immediately.
     * AC4: if an IN_PROGRESS export already exists, returns 202 with status=IN_PROGRESS.
     */
    @PostMapping("/api/v1/users/me/data-export")
    public ResponseEntity<Map<String, Object>> requestExport(
            @AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);
        // AC4: duplicate guard — do not start a second export while one is running
        if (dataExportRepository.existsByUserIdAndStatus(uid, ExportStatus.IN_PROGRESS)) {
            return ResponseEntity.accepted()
                    .body(Map.of("status", "IN_PROGRESS",
                                 "message", "Export already in progress"));
        }
        dataExportService.generateExport(uid);
        return ResponseEntity.accepted()
                .body(Map.of("status", "PROCESSING",
                             "message", "Export started — you will be notified when ready"));
    }

    /**
     * GET /api/v1/users/me/data-export/download
     * AC2: returns the most recent completed export as a downloadable JSON attachment.
     * AC3: @AuthenticationPrincipal ensures only the owner's data is returned.
     */
    @GetMapping("/api/v1/users/me/data-export/download")
    public ResponseEntity<String> downloadExport(
            @AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);
        DataExport export = dataExportRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(uid, ExportStatus.READY)
                .orElseThrow(() -> new BadRequestException("No export ready for download. "
                        + "Call POST /api/v1/users/me/data-export first."));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"unity-skill-export.json\"")
                .body(export.getExportJson());
    }
}
