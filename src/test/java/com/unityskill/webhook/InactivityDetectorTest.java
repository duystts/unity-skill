package com.unityskill.webhook;

import com.unityskill.notification.NotificationService;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivityDetectorTest {

    @Mock TicketRepository ticketRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @InjectMocks InactivityDetector detector;

    private Ticket inactiveTicket(UUID workspaceId, Instant lastAlertedAt) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .projectId(UUID.randomUUID())
                .title("Stuck ticket")
                .updatedAt(Instant.now().minus(72, ChronoUnit.HOURS))
                .lastAlertedAt(lastAlertedAt)
                .build();
    }

    private WorkspaceMember pmMember(UUID workspaceId) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .userId(UUID.randomUUID())
                .role(WorkspaceRole.PM)
                .build();
    }

    @Test
    void detectAndAlert_inactiveTicket_sendsNotificationsToPms() {
        UUID workspaceId = UUID.randomUUID();
        Ticket ticket = inactiveTicket(workspaceId, null); // never alerted
        WorkspaceMember pm = pmMember(workspaceId);

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));
        when(memberRepository.findAllByWorkspaceIdAndRoleIn(eq(workspaceId), anyList()))
                .thenReturn(List.of(pm));

        detector.detectAndAlert();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notify(
                eq(pm.getUserId()), eq(workspaceId), eq("TICKET_INACTIVE"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("ticketId")).isEqualTo(ticket.getId().toString());
        assertThat(payload.get("title")).isEqualTo("Stuck ticket");
        assertThat((long) payload.get("stuckForHours")).isGreaterThanOrEqualTo(71);
    }

    @Test
    void detectAndAlert_twoPmsInWorkspace_notifiesBoth() {
        UUID workspaceId = UUID.randomUUID();
        Ticket ticket = inactiveTicket(workspaceId, null);
        WorkspaceMember pm1 = pmMember(workspaceId);
        WorkspaceMember pm2 = pmMember(workspaceId);

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));
        when(memberRepository.findAllByWorkspaceIdAndRoleIn(eq(workspaceId), anyList()))
                .thenReturn(List.of(pm1, pm2));

        detector.detectAndAlert();

        verify(notificationService, times(2)).notify(any(), any(), eq("TICKET_INACTIVE"), any());
    }

    @Test
    void detectAndAlert_ticketAlertedWithin24h_skipsNotification() {
        UUID workspaceId = UUID.randomUUID();
        // Alerted 12 hours ago — within cooldown
        Ticket ticket = inactiveTicket(workspaceId,
                Instant.now().minus(12, ChronoUnit.HOURS));

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));

        detector.detectAndAlert();

        verifyNoInteractions(notificationService);
        verify(ticketRepository, never()).updateLastAlertedAt(any(), any());
    }

    @Test
    void detectAndAlert_ticketAlertedMoreThan24hAgo_sendsAgain() {
        UUID workspaceId = UUID.randomUUID();
        // Alerted 30 hours ago — cooldown expired
        Ticket ticket = inactiveTicket(workspaceId,
                Instant.now().minus(30, ChronoUnit.HOURS));
        WorkspaceMember pm = pmMember(workspaceId);

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));
        when(memberRepository.findAllByWorkspaceIdAndRoleIn(eq(workspaceId), anyList()))
                .thenReturn(List.of(pm));

        detector.detectAndAlert();

        verify(notificationService).notify(any(), any(), eq("TICKET_INACTIVE"), any());
    }

    @Test
    void detectAndAlert_noInactiveTickets_sendsNothing() {
        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of());

        detector.detectAndAlert();

        verifyNoInteractions(notificationService, memberRepository);
    }

    @Test
    void detectAndAlert_noPmsInWorkspace_updatesLastAlertedAtAnyway() {
        UUID workspaceId = UUID.randomUUID();
        Ticket ticket = inactiveTicket(workspaceId, null);

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));
        when(memberRepository.findAllByWorkspaceIdAndRoleIn(eq(workspaceId), anyList()))
                .thenReturn(List.of()); // no PMs

        detector.detectAndAlert();

        verifyNoInteractions(notificationService);
        verify(ticketRepository).updateLastAlertedAt(eq(ticket.getId()), any());
    }

    @Test
    void detectAndAlert_updatesLastAlertedAtAfterSendingNotification() {
        UUID workspaceId = UUID.randomUUID();
        Ticket ticket = inactiveTicket(workspaceId, null);
        WorkspaceMember pm = pmMember(workspaceId);

        when(ticketRepository.findAllByClosedAtIsNullAndUpdatedAtBefore(any()))
                .thenReturn(List.of(ticket));
        when(memberRepository.findAllByWorkspaceIdAndRoleIn(eq(workspaceId), anyList()))
                .thenReturn(List.of(pm));

        detector.detectAndAlert();

        // AC3: lastAlertedAt must be updated to prevent duplicate alerts
        verify(ticketRepository).updateLastAlertedAt(eq(ticket.getId()), any(Instant.class));
    }
}
