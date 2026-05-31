package com.unityskill.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/users/me/profile — update displayName, title, timezone, bio in one call.
 * All fields are optional (null = leave unchanged).
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100, message = "Display name must be 1–100 characters")
        String displayName,

        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,

        @Size(max = 60, message = "Timezone must be a valid IANA identifier")
        String timezone,

        @Size(max = 500, message = "Bio must be at most 500 characters")
        String bio
) {}
