package com.unityskill.project.dto;

import com.unityskill.project.entity.ProjectVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
    @NotBlank(message = "name is required") String name,
    String description,
    @NotNull(message = "visibility is required") ProjectVisibility visibility
) {}
