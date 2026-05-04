package com.unityskill.collaboration.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ChatMessageResponse {
    UUID id;
    String content;
    String senderName;
    Instant createdAt;
}
