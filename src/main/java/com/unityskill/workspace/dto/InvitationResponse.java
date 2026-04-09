package com.unityskill.workspace.dto;

import com.unityskill.workspace.entity.WorkspaceInvitation;

public record InvitationResponse(
        String id,
        String workspaceId,
        String email,
        String token,
        String type,
        String status,
        String expiresAt,
        String createdAt
) {
    public static InvitationResponse from(WorkspaceInvitation inv) {
        return new InvitationResponse(
                inv.getId().toString(),
                inv.getWorkspaceId().toString(),
                inv.getEmail(),
                inv.getToken(),
                inv.getType().name(),
                inv.getStatus().name(),
                inv.getExpiresAt().toString(),
                inv.getCreatedAt().toString()
        );
    }
}
