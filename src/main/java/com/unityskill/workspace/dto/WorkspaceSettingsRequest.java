package com.unityskill.workspace.dto;

import jakarta.validation.constraints.Min;

public record WorkspaceSettingsRequest(
        @Min(1) int overloadedThreshold,
        @Min(1) int balancedMinThreshold
) {}
