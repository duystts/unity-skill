package com.unityskill.achievement.dto;

import com.unityskill.achievement.entity.TicketTagValue;
import jakarta.validation.constraints.NotNull;

public record TicketTagRequest(@NotNull TicketTagValue tag) {}
