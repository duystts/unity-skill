package com.unityskill.auth.dto;

import com.unityskill.auth.entity.User;
import java.util.UUID;

public record AuthUserResponse(UUID id, String email, String displayName) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
