package com.unityskill.collaboration;

import com.unityskill.collaboration.dto.CreateMeetingRequest;
import com.unityskill.collaboration.dto.MeetingResponse;
import com.unityskill.collaboration.dto.UpdateMeetingRequest;
import com.unityskill.collaboration.entity.AgendaStatus;
import com.unityskill.collaboration.entity.Meeting;
import com.unityskill.collaboration.entity.MeetingStatus;
import com.unityskill.collaboration.entity.MeetingTranscript;
import com.unityskill.collaboration.entity.TranscriptStatus;
import com.unityskill.common.exception.MeetingNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock TranscriptRepository transcriptRepository;
    @InjectMocks MeetingService meetingService;

    UUID workspaceId;
    UUID projectId;
    UUID callerId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId   = UUID.randomUUID();
        callerId    = UUID.randomUUID();
    }

    private Meeting savedMeeting() {
        return Meeting.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(projectId)
                .title("Sprint Planning")
                .scheduledAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .createdBy(callerId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── createMeeting ─────────────────────────────────────────────────────

    @Test
    void createMeeting_member_persistsAndReturnsScheduledStatus() {
        Meeting saved = savedMeeting();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.save(any())).thenReturn(saved);

        CreateMeetingRequest req = new CreateMeetingRequest(
                "Sprint Planning", projectId, Instant.now().plus(1, ChronoUnit.DAYS));

        MeetingResponse response = meetingService.createMeeting(workspaceId, callerId, req);

        assertThat(response.getStatus()).isEqualTo(MeetingStatus.SCHEDULED.name());
        assertThat(response.getTitle()).isEqualTo("Sprint Planning");
        assertThat(response.getWorkspaceId()).isEqualTo(workspaceId);
        verify(meetingRepository).save(any(Meeting.class));
    }

    @Test
    void createMeeting_nonMember_throwsUnauthorized() {
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(false);
        assertThatThrownBy(() -> meetingService.createMeeting(workspaceId, callerId,
                new CreateMeetingRequest("Meeting", projectId, Instant.now())))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(meetingRepository);
    }

    // ── listMeetings ──────────────────────────────────────────────────────

    @Test
    void listMeetings_member_returnsOrderedList() {
        Meeting m1 = savedMeeting();
        Meeting m2 = savedMeeting();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findAllByWorkspaceIdOrderByScheduledAtAsc(workspaceId))
                .thenReturn(List.of(m1, m2));

        List<MeetingResponse> result = meetingService.listMeetings(workspaceId, callerId);

        assertThat(result).hasSize(2);
    }

    @Test
    void listMeetings_nonMember_throwsUnauthorized() {
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(false);
        assertThatThrownBy(() -> meetingService.listMeetings(workspaceId, callerId))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ── updateMeeting ─────────────────────────────────────────────────────

    @Test
    void updateMeeting_member_updatesAllowedFields() {
        Meeting existing = savedMeeting();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(meetingRepository.save(any())).thenReturn(existing);

        Instant newTime = Instant.now().plus(2, ChronoUnit.DAYS);
        UpdateMeetingRequest req = new UpdateMeetingRequest("Renamed Meeting", newTime);

        MeetingResponse response = meetingService.updateMeeting(
                workspaceId, existing.getId(), callerId, req);

        assertThat(existing.getTitle()).isEqualTo("Renamed Meeting");
        assertThat(existing.getScheduledAt()).isEqualTo(newTime);
        verify(meetingRepository).save(existing);
    }

    @Test
    void updateMeeting_nullFields_skipsUpdate() {
        Meeting existing = savedMeeting();
        String originalTitle = existing.getTitle();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(meetingRepository.save(any())).thenReturn(existing);

        meetingService.updateMeeting(workspaceId, existing.getId(), callerId,
                new UpdateMeetingRequest(null, null));

        assertThat(existing.getTitle()).isEqualTo(originalTitle); // unchanged
    }

    @Test
    void updateMeeting_wrongWorkspace_throwsNotFound() {
        Meeting meetingFromOtherWorkspace = Meeting.builder()
                .id(UUID.randomUUID())
                .workspaceId(UUID.randomUUID()) // different workspace
                .projectId(projectId)
                .title("Other workspace meeting")
                .scheduledAt(Instant.now())
                .build();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findById(meetingFromOtherWorkspace.getId()))
                .thenReturn(Optional.of(meetingFromOtherWorkspace));

        assertThatThrownBy(() -> meetingService.updateMeeting(
                workspaceId, meetingFromOtherWorkspace.getId(), callerId,
                new UpdateMeetingRequest("title", null)))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void updateMeeting_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.updateMeeting(
                workspaceId, unknownId, callerId, new UpdateMeetingRequest("title", null)))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    // ── getMeeting ────────────────────────────────────────────────────────

    @Test
    void getMeeting_member_returnsMeeting() {
        Meeting existing = savedMeeting();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(existing.getId(), workspaceId))
                .thenReturn(Optional.of(existing));
        when(transcriptRepository.findTopByMeetingIdOrderByCreatedAtDesc(existing.getId()))
                .thenReturn(Optional.empty()); // no transcript yet

        MeetingResponse response = meetingService.getMeeting(workspaceId, existing.getId(), callerId);

        assertThat(response.getTitle()).isEqualTo("Sprint Planning");
        assertThat(response.getTranscriptStatus()).isNull();
    }

    @Test
    void getMeeting_withProcessedTranscript_includesTranscriptStatus() {
        Meeting existing = savedMeeting();
        MeetingTranscript transcript = MeetingTranscript.builder()
                .id(UUID.randomUUID())
                .meetingId(existing.getId())
                .workspaceId(workspaceId)
                .rawContent("content")
                .status(TranscriptStatus.PROCESSED)
                .build();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(existing.getId(), workspaceId))
                .thenReturn(Optional.of(existing));
        when(transcriptRepository.findTopByMeetingIdOrderByCreatedAtDesc(existing.getId()))
                .thenReturn(Optional.of(transcript));

        MeetingResponse response = meetingService.getMeeting(workspaceId, existing.getId(), callerId);

        assertThat(response.getTranscriptStatus()).isEqualTo(TranscriptStatus.PROCESSED.name());
    }

    @Test
    void getMeeting_withProcessingTranscript_returnsProcessingStatus() {
        Meeting existing = savedMeeting();
        MeetingTranscript transcript = MeetingTranscript.builder()
                .id(UUID.randomUUID())
                .meetingId(existing.getId())
                .workspaceId(workspaceId)
                .rawContent("content")
                .status(TranscriptStatus.PROCESSING)
                .build();
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)).thenReturn(true);
        when(meetingRepository.findByIdAndWorkspaceId(existing.getId(), workspaceId))
                .thenReturn(Optional.of(existing));
        when(transcriptRepository.findTopByMeetingIdOrderByCreatedAtDesc(existing.getId()))
                .thenReturn(Optional.of(transcript));

        MeetingResponse response = meetingService.getMeeting(workspaceId, existing.getId(), callerId);

        assertThat(response.getTranscriptStatus()).isEqualTo(TranscriptStatus.PROCESSING.name());
    }

    // ── markAgendaGenerating ──────────────────────────────────────────────

    @Test
    void markAgendaGenerating_pmRole_setsGenerating() {
        Meeting existing = savedMeeting();
        WorkspaceMember pmMember = WorkspaceMember.builder()
                .workspaceId(workspaceId).userId(callerId).role(WorkspaceRole.PM).build();
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId))
                .thenReturn(Optional.of(pmMember));
        when(meetingRepository.findByIdAndWorkspaceId(existing.getId(), workspaceId))
                .thenReturn(Optional.of(existing));
        when(meetingRepository.save(any())).thenReturn(existing);

        meetingService.markAgendaGenerating(workspaceId, existing.getId(), callerId);

        assertThat(existing.getAgendaStatus()).isEqualTo(AgendaStatus.GENERATING);
        verify(meetingRepository).save(existing);
    }

    @Test
    void markAgendaGenerating_developerRole_throwsUnauthorized() {
        WorkspaceMember devMember = WorkspaceMember.builder()
                .workspaceId(workspaceId).userId(callerId).role(WorkspaceRole.DEVELOPER).build();
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, callerId))
                .thenReturn(Optional.of(devMember));

        assertThatThrownBy(() -> meetingService.markAgendaGenerating(workspaceId, UUID.randomUUID(), callerId))
                .isInstanceOf(UnauthorizedAccessException.class);
        verifyNoInteractions(meetingRepository);
    }
}
