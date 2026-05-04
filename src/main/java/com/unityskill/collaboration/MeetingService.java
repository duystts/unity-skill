package com.unityskill.collaboration;

import com.unityskill.collaboration.dto.CreateMeetingRequest;
import com.unityskill.collaboration.dto.MeetingResponse;
import com.unityskill.collaboration.dto.UpdateMeetingRequest;
import com.unityskill.collaboration.entity.AgendaStatus;
import com.unityskill.collaboration.entity.Meeting;
import com.unityskill.common.exception.MeetingNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final TranscriptRepository transcriptRepository;

    @Transactional
    public MeetingResponse createMeeting(UUID workspaceId, UUID callerId,
                                         CreateMeetingRequest req) {
        requireMember(workspaceId, callerId);

        Meeting meeting = Meeting.builder()
                .workspaceId(workspaceId)
                .projectId(req.projectId())
                .title(req.title())
                .scheduledAt(req.scheduledAt())
                .createdBy(callerId)
                .build();

        meeting = meetingRepository.save(meeting);
        return MeetingResponse.from(meeting);
    }

    public List<MeetingResponse> listMeetings(UUID workspaceId, UUID callerId) {
        requireMember(workspaceId, callerId);
        return meetingRepository.findAllByWorkspaceIdOrderByScheduledAtAsc(workspaceId)
                .stream()
                .map(MeetingResponse::from)
                .toList();
    }

    @Transactional
    public MeetingResponse updateMeeting(UUID workspaceId, UUID meetingId,
                                         UUID callerId, UpdateMeetingRequest req) {
        requireMember(workspaceId, callerId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(MeetingNotFoundException::new);

        // AC3: workspace isolation — return 404 if meeting belongs to a different workspace
        if (!meeting.getWorkspaceId().equals(workspaceId)) {
            throw new MeetingNotFoundException();
        }

        // Partial update — null fields are not applied (UpdateTicketRequest pattern)
        if (req.title() != null) {
            meeting.setTitle(req.title());
        }
        if (req.scheduledAt() != null) {
            meeting.setScheduledAt(req.scheduledAt());
        }

        meeting = meetingRepository.save(meeting);
        return MeetingResponse.from(meeting);
    }

    public MeetingResponse getMeeting(UUID workspaceId, UUID meetingId, UUID callerId) {
        requireMember(workspaceId, callerId);
        Meeting meeting = meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)
                .orElseThrow(MeetingNotFoundException::new);
        String transcriptStatus = transcriptRepository
                .findTopByMeetingIdOrderByCreatedAtDesc(meetingId)
                .map(t -> t.getStatus().name())
                .orElse(null);
        return MeetingResponse.from(meeting, transcriptStatus);
    }

    @Transactional
    public void markAgendaGenerating(UUID workspaceId, UUID meetingId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        Meeting meeting = meetingRepository.findByIdAndWorkspaceId(meetingId, workspaceId)
                .orElseThrow(MeetingNotFoundException::new);
        meeting.setAgendaStatus(AgendaStatus.GENERATING);
        meetingRepository.save(meeting);
    }

    private void requireMember(UUID workspaceId, UUID userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new UnauthorizedAccessException("Access denied: not a workspace member");
        }
    }

    private void requirePmOrAdmin(UUID workspaceId, UUID userId) {
        memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(m -> m.getRole() == WorkspaceRole.PM || m.getRole() == WorkspaceRole.ADMIN)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: PM or Admin role required"));
    }
}
