package com.unityskill.workspace.dto;

public record WorkspaceSettingsResponse(
        int overloadedThreshold,
        int balancedMinThreshold
) {}
