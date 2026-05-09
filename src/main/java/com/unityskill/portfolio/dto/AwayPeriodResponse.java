package com.unityskill.portfolio.dto;

public record AwayPeriodResponse(
        String id,
        String userId,
        String startDate,
        String endDate,
        String createdAt
) {}
