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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock TranscriptRepository transcriptRepository;
    @Mock MeetingRepository meetingRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock AiProvider aiProvider;
    @Mock WebSocketEventPublisher wsPublisher;
    @Mock ContributionService contributionService;
    @InjectMocks TranscriptService transcriptService;

    UUID workspaceId;
    UUID meetingId;
    UUID callerId;
    Meeting meeting;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        meetingId   = UUID.randomUUID();
        callerId    = UUID.randomUUID();

        meeting = Meeting.builder()
                .id(meetingId)
                .workspaceId(workspaceId)
                .projectId(UUID.randomUUID())
                .title("Sprint Planning")
                .scheduledAt(Instant.now())
                .build();
    }

    private MeetingTranscript savedTranscript(String content) {
        return MeetingTranscript.builder()
                .id(UUID.randomUUID())
                .meetingId(meetingId)
                .workspaceId(workspaceId)
                .rawContent(content)
                .createdAt(Instant.now())
                .build();
    }

    // ── uploadTranscript ──────────────────────────────────────────────────

    @Test
    void upload_vttFile_persistsAndReturnUploaded() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.vtt", "text/plain", "WEBVTT\n00:00.000 --> 00:01.000\nHello team".getBytes());
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)).thenReturn(Optional.of(meeting));
        when(transcriptRepository.save(any())).thenReturn(savedTranscript("WEBVTT\n..."));
        when(meetingRepository.save(any())).thenReturn(meeting);

        TranscriptResponse response = transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file);

        assertThat(response.getStatus()).isEqualTo(TranscriptStatus.UPLOADED.name());
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        verify(transcriptRepository).save(any(MeetingTranscript.class));
        verify(meetingRepository).save(meeting);
    }

    @Test
    void upload_txtFile_accepted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "Meeting notes here".getBytes());
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)).thenReturn(Optional.of(meeting));
        when(transcriptRepository.save(any())).thenReturn(savedTranscript("Meeting notes here"));
        when(meetingRepository.save(any())).thenReturn(meeting);

        TranscriptResponse response = transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file);

        assertThat(response).isNotNull();
    }

    @Test
    void upload_nonMember_throwsUnauthorized() {
        MockMultipartFile file = new MockMultipartFile("file", "f.vtt", "text/plain", new byte[0]);
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(false);

        assertThatThrownBy(() -> transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(transcriptRepository);
    }

    @Test
    void upload_meetingNotFound_throws404() {
        MockMultipartFile file = new MockMultipartFile("file", "f.vtt", "text/plain", new byte[0]);
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void upload_invalidExtension_throwsUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "recording.mp4", "video/mp4", new byte[]{1, 2, 3});
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining(".vtt and .txt");
    }

    @Test
    void upload_nullFilename_throwsUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/plain", new byte[0]);
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> transcriptService.uploadTranscript(workspaceId, meetingId, callerId, file))
                .isInstanceOf(UnsupportedFormatException.class);
    }

    // ── processAsync ──────────────────────────────────────────────────────

    @Test
    void processAsync_aiSuccess_populatesMeetingAndSetsProcessed() {
        UUID transcriptId = UUID.randomUUID();
        MeetingTranscript transcript = MeetingTranscript.builder()
                .id(transcriptId)
                .meetingId(meetingId)
                .workspaceId(workspaceId)
                .rawContent("WEBVTT\n00:00 --> 00:01\nLet's fix the login bug.")
                .build();

        when(transcriptRepository.findById(transcriptId)).thenReturn(Optional.of(transcript));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiProvider.generateText(any())).thenReturn(
                "SUMMARY:\nTeam discussed the login bug fix.\n\nACTION_ITEMS:\n[\"Fix login bug\", \"Update docs\"]");
        when(meetingRepository.save(any())).thenReturn(meeting);

        transcriptService.processAsync(transcriptId, callerId, workspaceId);

        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.PROCESSING);
        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.PROCESSED);
        assertThat(meeting.getSummary()).isEqualTo("Team discussed the login bug fix.");
        assertThat(meeting.getActionItems()).isEqualTo("[\"Fix login bug\", \"Update docs\"]");
        verify(wsPublisher).publishToTopic(contains(workspaceId.toString()), eq("TRANSCRIPT_PROCESSED"), any());
        // AC1 (Story 6.1): contribution extraction triggered after PROCESSED
        verify(contributionService).extractFromTranscript(eq(transcriptId), eq(workspaceId), anyString());
    }

    @Test
    void processAsync_aiThrows_setsFailedAndSwallowsException() {
        UUID transcriptId = UUID.randomUUID();
        MeetingTranscript transcript = MeetingTranscript.builder()
                .id(transcriptId)
                .meetingId(meetingId)
                .workspaceId(workspaceId)
                .rawContent("content")
                .build();

        when(transcriptRepository.findById(transcriptId)).thenReturn(Optional.of(transcript));
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiProvider.generateText(any())).thenThrow(new RuntimeException("AI timeout"));

        // Must not throw — catch block swallows exception (AC4)
        transcriptService.processAsync(transcriptId, callerId, workspaceId);

        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.PROCESSING);
        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.FAILED);
        verify(wsPublisher, never()).publishToTopic(any(), any(), any());
    }

    @Test
    void processAsync_transcriptNotFound_setsFailedGracefully() {
        UUID transcriptId = UUID.randomUUID();
        when(transcriptRepository.findById(transcriptId)).thenReturn(Optional.empty());

        // Must not throw even if transcript vanished
        transcriptService.processAsync(transcriptId, callerId, workspaceId);

        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.PROCESSING);
        verify(transcriptRepository).updateStatus(transcriptId, TranscriptStatus.FAILED);
        verify(aiProvider, never()).generateText(any());
    }
}
