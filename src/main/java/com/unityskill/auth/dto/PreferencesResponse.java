package com.unityskill.auth.dto;

import com.unityskill.auth.entity.User;

public record PreferencesResponse(String uiMode, boolean incognitoMode) {

    public static PreferencesResponse from(User user) {
        return new PreferencesResponse(
                user.getUiMode().name(),
                user.isIncognito()
        );
    }
}
