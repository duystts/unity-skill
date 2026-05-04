package com.unityskill.collaboration.dto;

import com.unityskill.collaboration.entity.MeetingTranscript;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class TranscriptResponse {

    UUID id;
    UUID meetingId;
    UUID workspaceId;
    String status;
    Instant createdAt;
    // rawContent intentionally excluded — may be large; consumers poll GET /meetings/{id}

    public static TranscriptResponse from(MeetingTranscript t) {
        return TranscriptResponse.builder()
                .id(t.getId())
                .meetingId(t.getMeetingId())
                .workspaceId(t.getWorkspaceId())
                .status(t.getStatus().name())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
