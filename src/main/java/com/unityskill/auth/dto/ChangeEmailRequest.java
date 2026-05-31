package com.unityskill.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank @Email(message = "Must be a valid email address")
        String newEmail,

        /** Required when the account has a password set; omit for OAuth-only accounts. */
        String currentPassword
) {}
