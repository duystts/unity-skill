package com.unityskill.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStageRequest(
        @NotBlank String name,
        @NotNull Integer position,
        Boolean isClosedState
) {}
