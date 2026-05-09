package com.unityskill.tracking.dto;

import com.unityskill.tracking.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrackingPermissionRequest(
        @NotNull(message = "resourceType is required")
        ResourceType resourceType,

        @NotBlank(message = "resourceId is required")
        String resourceId,

        @NotNull(message = "enabled is required")
        Boolean enabled
) {}
