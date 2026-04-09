package com.unityskill.auth.dto;

public record LoginResponse(AuthUserResponse user, String accessToken) {}
