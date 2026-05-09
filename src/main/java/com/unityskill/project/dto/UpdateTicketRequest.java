package com.unityskill.project.dto;

import com.unityskill.project.entity.AssignmentMode;

import java.util.UUID;

public record UpdateTicketRequest(
    String title,
    String description,
    UUID stageId,
    UUID assigneeId,
    AssignmentMode assignmentMode,
    String githubPrUrl
) {}
