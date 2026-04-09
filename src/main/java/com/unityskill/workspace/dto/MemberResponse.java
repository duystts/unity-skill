package com.unityskill.workspace.dto;

import com.unityskill.auth.entity.User;
import com.unityskill.workspace.entity.WorkspaceMember;

public record MemberResponse(
        String userId,
        String email,
        String displayName,
        String role,
        String joinedAt
) {
    public static MemberResponse from(WorkspaceMember member, User user) {
        return new MemberResponse(
                member.getUserId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                member.getRole().name(),
                member.getCreatedAt().toString()
        );
    }
}
