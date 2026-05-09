package com.unityskill.auth.dto;

import com.unityskill.auth.entity.UiMode;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferencesRequest(
    @NotNull(message = "uiMode is required")
    UiMode uiMode
) {}
