package com.unityskill.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateMeetingRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull UUID projectId,
        @NotNull Instant scheduledAt,
        @Size(max = 2048) String meetingUrl   // optional — Google Meet / Zoom / Teams link
) {}
