package com.unityskill.achievement.dto;

import com.unityskill.achievement.entity.TicketTagValue;

import java.util.List;

public record TagsResponse(List<TicketTagValue> tags) {}
