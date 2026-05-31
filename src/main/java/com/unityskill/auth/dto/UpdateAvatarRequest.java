package com.unityskill.auth.dto;

/** Null avatarUrl removes the avatar (resets to initials). */
public record UpdateAvatarRequest(String avatarUrl) {}
