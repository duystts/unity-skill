package com.unityskill.project.dto;

public record UpdateStageRequest(
        String name,
        Integer position,
        Boolean isClosedState
) {}
