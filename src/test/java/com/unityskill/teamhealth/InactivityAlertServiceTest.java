package com.unityskill.teamhealth;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.collaboration.ChatRepository;
import com.unityskill.collaboration.entity.ChatMessage;
import com.unityskill.contribution.ContributionRepository;
import com.unityskill.notification.NotificationService;
import com.unityskill.project.TicketRepository;
import com.unityskill.project.entity.Ticket;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.WorkspaceRepository;
import com.unityskill.workspace.entity.Workspace;
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
class InactivityAlertServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock TicketRepository ticketRepository;
    @Mock ChatRepository chatRepository;
    @Mock ContributionRepository contributionRepository;
    @Mock NotificationService notificationService;
    @InjectMocks InactivityAlertService service;

    private Workspace workspace(UUID id) {
        return Workspace.builder().id(id).name("Test Workspace").createdBy(UUID.randomUUID()).slug("test").build();
    }

    private WorkspaceMember member(UUID workspaceId, UUID userId, WorkspaceRole role,
                                   Instant lastAlerted) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId)
                .userId(userId).role(role)
                .lastInactivityAlertedAt(lastAlerted).build();
    }

    private User user(UUID id, String name) {
        return User.builder().id(id).email(name + "@e.com").displayName(name).build();
    }

    private void stubNoActivity(UUID workspaceId) {
        when(ticketRepository.findAllByWorkspaceIdAndAssigneeIdIsNotNullAndUpdatedAtAfter(
                eq(workspaceId), any())).thenReturn(List.of());
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of());
        when(contributionRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of());
    }

    @Test
    void detectInactivity_inactiveMember_notifiesAllPmsWithCorrectPayload() {
        // AC2: inactive developer → MEMBER_INACTIVE sent to PM
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();

        WorkspaceMember dev = member(workspaceId, devId, WorkspaceRole.DEVELOPER, null);
        WorkspaceMember pm  = member(workspaceId, pmId,  WorkspaceRole.PM, null);

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(workspaceId)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(dev, pm));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(user(devId, "Alice"), user(pmId, "Bob")));
        stubNoActivity(workspaceId);

        service.detectAndAlertMemberInactivity();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notify(
                eq(pmId), eq(workspaceId), eq("MEMBER_INACTIVE"), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("userId")).isEqualTo(devId.toString());
        assertThat(payload.get("memberName")).isEqualTo("Alice");
        assertThat((long) payload.get("inactiveDays")).isGreaterThan(
                InactivityAlertService.INACTIVITY_THRESHOLD_DAYS);

        // AC3: update cooldown timestamp for the dev member
        verify(memberRepository).updateLastInactivityAlertedAt(eq(dev.getId()), any(Instant.class));
    }

    @Test
    void detectInactivity_alreadyAlertedWithin24h_skipsNotification() {
        // AC3: member already alerted 12h ago → no duplicate
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();

        // Alerted 12 hours ago — within 24h cooldown
        Instant recentAlert = Instant.now().minus(12, ChronoUnit.HOURS);
        WorkspaceMember dev = member(workspaceId, devId, WorkspaceRole.DEVELOPER, recentAlert);
        WorkspaceMember pm  = member(workspaceId, pmId,  WorkspaceRole.PM, null);

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(workspaceId)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(dev, pm));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(user(devId, "Alice"), user(pmId, "Bob")));
        stubNoActivity(workspaceId);

        service.detectAndAlertMemberInactivity();

        // AC3: no notification sent, no cooldown update
        verifyNoInteractions(notificationService);
        verify(memberRepository, never()).updateLastInactivityAlertedAt(any(), any());
    }

    @Test
    void detectInactivity_memberHasTicketActivity_isNotConsideredInactive() {
        // AC4: active member (ticket updated after cutoff) → no alert
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();

        WorkspaceMember dev = member(workspaceId, devId, WorkspaceRole.DEVELOPER, null);
        WorkspaceMember pm  = member(workspaceId, pmId,  WorkspaceRole.PM, null);

        // Ticket updated recently by the developer
        Ticket recentTicket = Ticket.builder()
                .id(UUID.randomUUID()).workspaceId(workspaceId)
                .assigneeId(devId).title("Active ticket")
                .updatedAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(workspaceId)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(dev, pm));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(user(devId, "Alice"), user(pmId, "Bob")));
        when(ticketRepository.findAllByWorkspaceIdAndAssigneeIdIsNotNullAndUpdatedAtAfter(
                eq(workspaceId), any())).thenReturn(List.of(recentTicket));
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of());
        when(contributionRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of());

        service.detectAndAlertMemberInactivity();

        // AC4: developer is active → no notification sent
        verifyNoInteractions(notificationService);
    }

    @Test
    void detectInactivity_noInactiveMembers_sendsNothing() {
        // Edge case: all members in workspace are active via chat
        UUID workspaceId = UUID.randomUUID();
        UUID devId       = UUID.randomUUID();
        UUID pmId        = UUID.randomUUID();

        WorkspaceMember dev = member(workspaceId, devId, WorkspaceRole.DEVELOPER, null);
        WorkspaceMember pm  = member(workspaceId, pmId,  WorkspaceRole.PM, null);

        // Both have recent chat activity
        ChatMessage devMsg = ChatMessage.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .projectId(UUID.randomUUID()).senderId(devId).content("Hello").createdAt(Instant.now()).build();
        ChatMessage pmMsg  = ChatMessage.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .projectId(UUID.randomUUID()).senderId(pmId).content("Hi").createdAt(Instant.now()).build();

        when(workspaceRepository.findAll()).thenReturn(List.of(workspace(workspaceId)));
        when(memberRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(dev, pm));
        when(userRepository.findAllById(anyList()))
                .thenReturn(List.of(user(devId, "Alice"), user(pmId, "Bob")));
        when(ticketRepository.findAllByWorkspaceIdAndAssigneeIdIsNotNullAndUpdatedAtAfter(
                eq(workspaceId), any())).thenReturn(List.of());
        when(chatRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of(devMsg, pmMsg));
        when(contributionRepository.findAllByWorkspaceIdAndCreatedAtAfter(eq(workspaceId), any()))
                .thenReturn(List.of());

        service.detectAndAlertMemberInactivity();

        verifyNoInteractions(notificationService);
        verify(memberRepository, never()).updateLastInactivityAlertedAt(any(), any());
    }
}
