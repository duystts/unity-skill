package com.unityskill.project.dto;

import com.unityskill.project.entity.TriggerType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTriggerRuleRequest(
        @NotNull TriggerType triggerType,
        @NotNull UUID targetStageId
) {}
