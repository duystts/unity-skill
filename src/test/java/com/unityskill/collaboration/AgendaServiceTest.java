package com.unityskill.collaboration;

import com.unityskill.ai.AiProvider;
import com.unityskill.collaboration.entity.AgendaStatus;
import com.unityskill.collaboration.entity.Meeting;
import com.unityskill.notification.WebSocketEventPublisher;
import com.unityskill.project.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock TicketRepository ticketRepository;
    @Mock ChatRepository chatRepository;
    @Mock AiProvider aiProvider;
    @Mock WebSocketEventPublisher wsPublisher;
    @InjectMocks AgendaService agendaService;

    UUID meetingId;
    UUID requesterId;
    UUID workspaceId;
    UUID projectId;
    Meeting meeting;

    @BeforeEach
    void setUp() {
        meetingId   = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId   = UUID.randomUUID();

        meeting = Meeting.builder()
                .id(meetingId)
                .workspaceId(workspaceId)
                .projectId(projectId)
                .title("Sprint Planning")
                .scheduledAt(Instant.now())
                .build();
    }

    @Test
    void executeGeneration_aiSuccess_setsReadyAndNotifiesRequester() {
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(ticketRepository.findAllByProjectIdAndClosedAtIsNull(projectId)).thenReturn(List.of());
        when(chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(aiProvider.generateText(any())).thenReturn("1. Review open tickets\n2. Sprint goals");

        agendaService.executeGeneration(meetingId, requesterId, workspaceId);

        assertThat(meeting.getAgenda()).isEqualTo("1. Review open tickets\n2. Sprint goals");
        assertThat(meeting.getAgendaStatus()).isEqualTo(AgendaStatus.READY);
        verify(meetingRepository).save(meeting);
        verify(wsPublisher).publishNotification(eq(requesterId), eq("AGENDA_READY"), any());
    }

    @Test
    void executeGeneration_aiThrows_setsFailedAndSwallowsException() {
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(ticketRepository.findAllByProjectIdAndClosedAtIsNull(projectId)).thenReturn(List.of());
        when(chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(aiProvider.generateText(any())).thenThrow(new RuntimeException("AI timeout"));

        // Must not throw — AC4
        agendaService.executeGeneration(meetingId, requesterId, workspaceId);

        assertThat(meeting.getAgendaStatus()).isEqualTo(AgendaStatus.FAILED);
        verify(wsPublisher, never()).publishNotification(any(), any(), any());
    }

    @Test
    void executeGeneration_meetingNotFound_doesNotThrow() {
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.empty());

        // Must not throw even if meeting vanished — AC4
        agendaService.executeGeneration(meetingId, requesterId, workspaceId);

        verify(aiProvider, never()).generateText(any());
    }

    @Test
    void executeGeneration_includesContextFromTicketsAndChat() {
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(ticketRepository.findAllByProjectIdAndClosedAtIsNull(projectId)).thenReturn(List.of());
        when(chatRepository.findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(aiProvider.generateText(any())).thenReturn("agenda");

        agendaService.executeGeneration(meetingId, requesterId, workspaceId);

        verify(ticketRepository).findAllByProjectIdAndClosedAtIsNull(projectId);
        verify(chatRepository).findAllByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(
                eq(workspaceId), eq(projectId), any());
    }
}
