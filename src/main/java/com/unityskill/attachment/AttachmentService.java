package com.unityskill.attachment;

import com.unityskill.attachment.dto.StorageStatsResponse;
import com.unityskill.attachment.dto.TicketAttachmentResponse;
import com.unityskill.attachment.entity.TicketAttachment;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.project.TicketRepository;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** Free tier storage limit per workspace: 500 MB */
    @Value("${app.storage.limit-bytes:524288000}")
    private long storageLimitBytes;

    private static final List<String> ALLOWED_TYPES = List.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "video/mp4", "video/webm", "video/quicktime"
    );

    private final TicketAttachmentRepository attachmentRepository;
    private final CloudinaryService cloudinaryService;
    private final TicketRepository ticketRepository;
    private final WorkspaceMemberRepository memberRepository;

    @Transactional
    public TicketAttachmentResponse upload(
            UUID workspaceId, UUID projectId, UUID ticketId,
            UUID uploaderId, MultipartFile file) throws IOException {

        requireMember(workspaceId, uploaderId);

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not supported: " + file.getContentType());
        }

        // Storage quota check
        long used = attachmentRepository.sumBytesByWorkspaceId(workspaceId);
        if (used + file.getSize() > storageLimitBytes) {
            throw new IllegalStateException("Storage limit reached. Upgrade your plan to upload more files.");
        }

        ticketRepository.findById(ticketId)
            .filter(t -> t.getProjectId().equals(projectId))
            .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        String folder = "unity-skill/" + workspaceId + "/" + projectId + "/" + ticketId;
        Map<String, Object> result = cloudinaryService.upload(file, folder);

        TicketAttachment attachment = TicketAttachment.builder()
            .ticketId(ticketId)
            .projectId(projectId)
            .workspaceId(workspaceId)
            .uploaderId(uploaderId)
            .fileName(file.getOriginalFilename())
            .cloudinaryPublicId((String) result.get("public_id"))
            .url((String) result.get("secure_url"))
            .resourceType((String) result.get("resource_type"))
            .bytes(((Number) result.getOrDefault("bytes", file.getSize())).longValue())
            .format((String) result.get("format"))
            .build();

        return TicketAttachmentResponse.from(attachmentRepository.save(attachment));
    }

    public List<TicketAttachmentResponse> list(UUID workspaceId, UUID ticketId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return attachmentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId)
            .stream().map(TicketAttachmentResponse::from).toList();
    }

    @Transactional
    public void delete(UUID workspaceId, UUID attachmentId, UUID callerId) throws IOException {
        requireMember(workspaceId, callerId);
        TicketAttachment a = attachmentRepository.findById(attachmentId)
            .filter(att -> att.getWorkspaceId().equals(workspaceId))
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        cloudinaryService.delete(a.getCloudinaryPublicId(), a.getResourceType());
        attachmentRepository.delete(a);
    }

    public StorageStatsResponse stats(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        long used  = attachmentRepository.sumBytesByWorkspaceId(workspaceId);
        long count = attachmentRepository.countByWorkspaceId(workspaceId);
        return new StorageStatsResponse(used, storageLimitBytes, count);
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Not a workspace member");
        }
    }
}
