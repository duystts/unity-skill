package com.unityskill.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiChatRequest(
        @NotBlank @Size(max = 2000) String message,
        List<ChatTurn> history          // may be null / empty for first message
) {
    public record ChatTurn(String role, String content) {}
}
