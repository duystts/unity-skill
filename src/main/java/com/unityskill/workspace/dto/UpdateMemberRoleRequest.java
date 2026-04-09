package com.unityskill.workspace.dto;

import com.unityskill.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
    @NotNull(message = "Role is required")
    WorkspaceRole role
) {}
