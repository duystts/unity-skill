package com.unityskill.auth.dto;

import com.unityskill.auth.entity.User;

/**
 * Full account info returned by GET /api/v1/users/me/account.
 * Used by the Account Settings page to populate all fields at once.
 */
public record AccountResponse(
        String id,
        String email,
        String displayName,
        String avatarUrl,
        String title,
        String timezone,
        String bio,
        String uiMode,
        boolean isIncognito,
        boolean hasPassword,        // false for OAuth-only users
        boolean githubConnected,
        boolean googleConnected,
        String createdAt,
        String updatedAt
) {
    public static AccountResponse from(User u) {
        return new AccountResponse(
                u.getId().toString(),
                u.getEmail(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getTitle(),
                u.getTimezone() != null ? u.getTimezone() : "Asia/Ho_Chi_Minh",
                u.getBio(),
                u.getUiMode() != null ? u.getUiMode().name() : "SERIOUS",
                u.isIncognito(),
                u.getPasswordHash() != null && !u.getPasswordHash().isBlank(),
                u.getGithubId() != null,
                u.getGoogleId() != null,
                u.getCreatedAt().toString(),
                u.getUpdatedAt().toString()
        );
    }
}
