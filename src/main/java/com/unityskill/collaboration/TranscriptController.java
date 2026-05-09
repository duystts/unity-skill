package com.unityskill.collaboration;

import com.unityskill.collaboration.dto.TranscriptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/meetings/{meetingId}/transcript")
@RequiredArgsConstructor
public class TranscriptController {

    private final TranscriptService transcriptService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadTranscript(
            @PathVariable UUID workspaceId,
            @PathVariable UUID meetingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String userId) {
        UUID callerId = UUID.fromString(userId);
        TranscriptResponse response = transcriptService.uploadTranscript(
                workspaceId, meetingId, callerId, file);
        // Trigger async AI processing — @Async proxy on transcriptService (cross-bean call)
        transcriptService.processAsync(response.getId(), callerId, workspaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", response));
    }
}
