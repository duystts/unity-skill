package com.unityskill.auth.dto;

public record RegisterResponse(AuthUserResponse user, String accessToken) {}
