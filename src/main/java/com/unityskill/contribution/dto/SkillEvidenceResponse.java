package com.unityskill.contribution.dto;

import com.unityskill.contribution.entity.SkillEvidence;

public record SkillEvidenceResponse(
        String id,
        String userId,
        String workspaceId,
        String status,
        String skillCategory,
        String aiSummary,
        String developerNotes,
        String sourceEvents,
        String reviewedAt,
        String createdAt,
        String updatedAt,
        boolean isPublished,    // Story 7.2
        String publishedAt      // Story 7.2 (null if not published)
) {
    public static SkillEvidenceResponse from(SkillEvidence e) {
        return new SkillEvidenceResponse(
                e.getId().toString(),
                e.getUserId().toString(),
                e.getWorkspaceId().toString(),
                e.getStatus().name(),
                e.getSkillCategory(),
                e.getAiSummary(),
                e.getDeveloperNotes(),
                e.getSourceEvents(),
                e.getReviewedAt()  != null ? e.getReviewedAt().toString()  : null,
                e.getCreatedAt()   != null ? e.getCreatedAt().toString()    : null,
                e.getUpdatedAt()   != null ? e.getUpdatedAt().toString()    : null,
                e.isPublished(),
                e.getPublishedAt() != null ? e.getPublishedAt().toString()  : null
        );
    }
}
