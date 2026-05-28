package com.unityskill.attachment;

import com.unityskill.attachment.dto.StorageStatsResponse;
import com.unityskill.attachment.dto.TicketAttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(
        value = "/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets/{ticketId}/attachments",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ticketId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String userId) throws IOException {
        TicketAttachmentResponse resp = attachmentService.upload(
            workspaceId, projectId, ticketId, UUID.fromString(userId), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", resp));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets/{ticketId}/attachments")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal String userId) {
        List<TicketAttachmentResponse> list = attachmentService.list(
            workspaceId, ticketId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", list));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/tickets/{ticketId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID ticketId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal String userId) throws IOException {
        attachmentService.delete(workspaceId, attachmentId, UUID.fromString(userId));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/storage/stats")
    public ResponseEntity<Map<String, Object>> storageStats(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal String userId) {
        StorageStatsResponse stats = attachmentService.stats(workspaceId, UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", stats));
    }
}
