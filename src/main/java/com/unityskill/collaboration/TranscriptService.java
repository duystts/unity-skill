package com.unityskill.collaboration;

import com.unityskill.ai.AiProvider;
import com.unityskill.collaboration.dto.TranscriptResponse;
import com.unityskill.contribution.ContributionService;
import com.unityskill.collaboration.entity.Meeting;
import com.unityskill.collaboration.entity.MeetingStatus;
import com.unityskill.collaboration.entity.MeetingTranscript;
import com.unityskill.collaboration.entity.TranscriptStatus;
import com.unityskill.common.exception.MeetingNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.UnsupportedFormatException;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.workspace.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".vtt", ".txt");

    private final TranscriptRepository transcriptRepository;
    private final MeetingRepository meetingRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final AiProvider aiProvider;
    private final WebSocketEventPublisher wsPublisher;
    private final ContributionService contributionService;

    @Transactional
    public TranscriptResponse uploadTranscript(UUID workspaceId, UUID meetingId,
                                               UUID callerId, MultipartFile file) {
        requireMember(workspaceId, callerId);

        // Workspace isolation — 404 if meeting doesn't belong to this workspace
        var meeting = meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)
                .orElseThrow(MeetingNotFoundException::new);

        // Validate file extension (AC3)
        validateExtension(file.getOriginalFilename());

        // Read file content as UTF-8 string
        String rawContent;
        try {
            rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UnsupportedFormatException("Failed to read file content: " + e.getMessage());
        }

        // Persist transcript (status defaults to UPLOADED)
        MeetingTranscript transcript = MeetingTranscript.builder()
                .meetingId(meetingId)
                .workspaceId(workspaceId)
                .rawContent(rawContent)
                .build();
        transcript = transcriptRepository.save(transcript);

        // Update meeting status to COMPLETED (AC4)
        meeting.setStatus(MeetingStatus.COMPLETED);
        meetingRepository.save(meeting);

        return TranscriptResponse.from(transcript);
    }

    /**
     * Asynchronously processes an uploaded transcript:
     * sets PROCESSING → calls AI for summary + action items → sets PROCESSED.
     * On any failure (including exhausted @Retryable retries): sets FAILED silently.
     * Called from TranscriptController after uploadTranscript() returns (cross-bean call — @Async proxy active).
     */
    @Async("taskExecutor")
    public void processAsync(UUID transcriptId, UUID uploaderId, UUID workspaceId) {
        transcriptRepository.updateStatus(transcriptId, TranscriptStatus.PROCESSING);
        try {
            MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                    .orElseThrow(() -> new IllegalArgumentException("Transcript not found: " + transcriptId));

            Meeting meeting = meetingRepository.findById(transcript.getMeetingId())
                    .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + transcript.getMeetingId()));

            // AI call — @Retryable on AiPipelineService.generateText() handles retries (AC4)
            String aiResult = aiProvider.generateText(buildPrompt(transcript.getRawContent()));

            meeting.setSummary(parseSummary(aiResult));
            meeting.setActionItems(parseActionItems(aiResult));
            meetingRepository.save(meeting);

            transcriptRepository.updateStatus(transcriptId, TranscriptStatus.PROCESSED);

            // Broadcast to all workspace members (no per-meeting participant list exists)
            wsPublisher.publishToTopic(
                    "/topic/workspace/" + workspaceId + "/meetings",
                    "TRANSCRIPT_PROCESSED",
                    Map.of(
                            "meetingId", meeting.getId().toString(),
                            "transcriptId", transcriptId.toString()
                    )
            );

            // Trigger async contribution signal extraction (Story 6.1 AC1)
            contributionService.extractFromTranscript(transcriptId, workspaceId, transcript.getRawContent());

        } catch (Exception e) {
            log.error("Transcript processing failed for transcript {}: {}", transcriptId, e.getMessage());
            transcriptRepository.updateStatus(transcriptId, TranscriptStatus.FAILED);
        }
    }

    private String buildPrompt(String rawContent) {
        return """
                Analyze the following meeting transcript and provide:
                1. A concise summary (3-5 sentences) of what was discussed and decided.
                2. A JSON array of action items extracted from the discussion.

                Format your response EXACTLY as:
                SUMMARY:
                <summary text here>

                ACTION_ITEMS:
                ["action item 1", "action item 2", ...]

                Transcript:
                """ + rawContent;
    }

    private String parseSummary(String aiResult) {
        int summaryStart = aiResult.indexOf("SUMMARY:");
        int actionItemsStart = aiResult.indexOf("ACTION_ITEMS:");
        if (summaryStart >= 0 && actionItemsStart > summaryStart) {
            return aiResult.substring(summaryStart + 8, actionItemsStart).strip();
        }
        return aiResult.strip(); // fallback: return full response as summary
    }

    private String parseActionItems(String aiResult) {
        int actionItemsStart = aiResult.indexOf("ACTION_ITEMS:");
        if (actionItemsStart >= 0) {
            String after = aiResult.substring(actionItemsStart + 13).strip();
            int jsonStart = after.indexOf('[');
            int jsonEnd = after.lastIndexOf(']');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return after.substring(jsonStart, jsonEnd + 1);
            }
        }
        return "[]"; // fallback: empty array
    }

    private void validateExtension(String filename) {
        if (filename == null) {
            throw new UnsupportedFormatException("Only .vtt and .txt files are accepted");
        }
        String lower = filename.toLowerCase();
        boolean valid = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!valid) {
            throw new UnsupportedFormatException("Only .vtt and .txt files are accepted");
        }
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }
    }
}
