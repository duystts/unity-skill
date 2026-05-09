package com.unityskill.project.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateTicketRequest(
    @NotBlank String title,
    UUID stageId,
    String description
) {}
