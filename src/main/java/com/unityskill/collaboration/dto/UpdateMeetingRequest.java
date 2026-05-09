package com.unityskill.collaboration.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateMeetingRequest(
        @Size(max = 255) String title,     // null = do not update
        Instant scheduledAt                // null = do not update
) {}
