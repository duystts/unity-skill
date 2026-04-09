package com.unityskill.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteByEmailRequest(
    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    String email
) {}
