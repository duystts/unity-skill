package com.unityskill.webhook.dto;

public record GithubConnectionResponse(boolean connected, String repoFullName) {}
