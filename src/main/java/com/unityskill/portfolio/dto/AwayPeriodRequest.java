package com.unityskill.portfolio.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AwayPeriodRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
