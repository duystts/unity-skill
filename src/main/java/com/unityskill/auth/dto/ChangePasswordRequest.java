package com.unityskill.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        /** Current password — required to verify identity before changing. Null for OAuth-only users setting a password for the first time. */
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String newPassword
) {}
