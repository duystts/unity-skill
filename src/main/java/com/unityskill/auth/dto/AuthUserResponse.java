package com.unityskill.auth.dto;

import com.unityskill.auth.entity.User;
import java.util.UUID;

public record AuthUserResponse(UUID id, String email, String displayName, String uiMode) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getUiMode().name()
        );
    }
}
