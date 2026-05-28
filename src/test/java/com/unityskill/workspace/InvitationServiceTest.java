package com.unityskill.workspace;

import com.unityskill.common.exception.AlreadyMemberException;
import com.unityskill.common.exception.InvitationNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.mail.MailService;
import com.unityskill.workspace.dto.InvitationResponse;
import com.unityskill.workspace.dto.InviteLinkResponse;
import com.unityskill.workspace.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock WorkspaceInvitationRepository invitationRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock MailService mailService;

    @InjectMocks InvitationService invitationService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FRONTEND_URL = "http://localhost:3000";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(invitationService, "frontendUrl", FRONTEND_URL);
    }

    private WorkspaceMember adminMember() {
        return WorkspaceMember.builder()
                .workspaceId(WORKSPACE_ID)
                .userId(ADMIN_ID)
                .role(WorkspaceRole.ADMIN)
                .build();
    }

    private WorkspaceMember developerMember() {
        return WorkspaceMember.builder()
                .workspaceId(WORKSPACE_ID)
                .userId(ADMIN_ID)
                .role(WorkspaceRole.DEVELOPER)
                .build();
    }

    private WorkspaceInvitation savedInvitation(InvitationType type) {
        return WorkspaceInvitation.builder()
                .id(UUID.randomUUID())
                .workspaceId(WORKSPACE_ID)
                .invitedBy(ADMIN_ID)
                .email(type == InvitationType.EMAIL ? "user@example.com" : null)
                .token("abc123token456def789")
                .type(type)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(604800))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ─── inviteByEmail ───

    @Test
    void inviteByEmail_success_returnsInvitationResponse() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(adminMember()));
        when(workspaceRepository.findById(WORKSPACE_ID))
                .thenReturn(Optional.of(new Workspace()));
        WorkspaceInvitation saved = savedInvitation(InvitationType.EMAIL);
        when(invitationRepository.saveAndFlush(any())).thenReturn(saved);

        InvitationResponse result = invitationService.inviteByEmail(
                WORKSPACE_ID, "user@example.com", ADMIN_ID);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.type()).isEqualTo("EMAIL");
        assertThat(result.email()).isEqualTo("user@example.com");
        verify(invitationRepository).save(any(WorkspaceInvitation.class));
    }

    @Test
    void inviteByEmail_notAdmin_throwsUnauthorizedAccessException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(Optional.of(developerMember()));

        assertThatThrownBy(() -> invitationService.inviteByEmail(
                WORKSPACE_ID, "user@example.com", USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteByEmail_notMember_throwsUnauthorizedAccessException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.inviteByEmail(
                WORKSPACE_ID, "user@example.com", USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ─── generateInviteLink ───

    @Test
    void generateInviteLink_success_returnsInviteLinkResponse() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(adminMember()));
        when(workspaceRepository.findById(WORKSPACE_ID))
                .thenReturn(Optional.of(new Workspace()));

        InviteLinkResponse result = invitationService.generateInviteLink(WORKSPACE_ID, ADMIN_ID);

        assertThat(result.inviteUrl()).startsWith(FRONTEND_URL + "/invite/");
        assertThat(result.token()).isNotBlank();
    }

    @Test
    void generateInviteLink_notAdmin_throwsUnauthorizedAccessException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(Optional.of(developerMember()));

        assertThatThrownBy(() -> invitationService.generateInviteLink(WORKSPACE_ID, USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ─── acceptInvitation ───

    @Test
    void acceptInvitation_success_addsMemberAndReturnsAccepted() {
        WorkspaceInvitation invitation = savedInvitation(InvitationType.LINK);
        when(invitationRepository.findByToken("abc123token456def789"))
                .thenReturn(Optional.of(invitation));
        when(memberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(false);
        when(memberRepository.save(any())).thenReturn(new WorkspaceMember());
        when(invitationRepository.save(any())).thenAnswer(inv -> {
            WorkspaceInvitation updated = inv.getArgument(0);
            updated.setStatus(InvitationStatus.ACCEPTED);
            return updated;
        });

        InvitationResponse result = invitationService.acceptInvitation("abc123token456def789", USER_ID);

        assertThat(result.status()).isEqualTo("ACCEPTED");
        var memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(WorkspaceRole.DEVELOPER);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void acceptInvitation_alreadyMember_throwsAlreadyMemberException() {
        WorkspaceInvitation invitation = savedInvitation(InvitationType.LINK);
        when(invitationRepository.findByToken("abc123token456def789"))
                .thenReturn(Optional.of(invitation));
        when(memberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> invitationService.acceptInvitation("abc123token456def789", USER_ID))
                .isInstanceOf(AlreadyMemberException.class)
                .hasMessage("Already a member");
    }

    @Test
    void acceptInvitation_tokenNotFound_throwsInvitationNotFoundException() {
        when(invitationRepository.findByToken("badtoken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.acceptInvitation("badtoken", USER_ID))
                .isInstanceOf(InvitationNotFoundException.class);
    }
}
