package com.unityskill.portfolio.dto;

import com.unityskill.portfolio.entity.Endorsement;

public record EndorsementResponse(
        String id,
        String evidenceId,
        String endorserId,
        String endorserName,
        String workspaceId,
        String createdAt
) {
    public static EndorsementResponse from(Endorsement e, String endorserName) {
        return new EndorsementResponse(
                e.getId().toString(),
                e.getEvidenceId().toString(),
                e.getEndorserId().toString(),
                endorserName,
                e.getWorkspaceId().toString(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
        );
    }
}
