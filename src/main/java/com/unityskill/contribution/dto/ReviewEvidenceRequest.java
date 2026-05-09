package com.unityskill.contribution.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewEvidenceRequest(
        @NotNull ReviewAction action,
        String developerNotes
) {}
