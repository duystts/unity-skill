package com.unityskill.workspace;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.LastAdminException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.workspace.dto.MemberResponse;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock WorkspaceMemberRepository memberRepository;
    @Mock UserRepository userRepository;

    @InjectMocks MemberService memberService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID     = UUID.randomUUID();
    private static final UUID TARGET_ID    = UUID.randomUUID();

    private WorkspaceMember member(UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspaceId(WORKSPACE_ID)
                .userId(userId)
                .role(role)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private User user(UUID id, String email, String name) {
        return User.builder()
                .id(id)
                .email(email)
                .displayName(name)
                .build();
    }

    // ─── listMembers ───

    @Test
    void listMembers_success_returnsMemberListWithUserData() {
        WorkspaceMember adminMember  = member(ADMIN_ID, WorkspaceRole.ADMIN);
        WorkspaceMember devMember    = member(TARGET_ID, WorkspaceRole.DEVELOPER);
        when(memberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID)).thenReturn(true);
        when(memberRepository.findAllByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(adminMember, devMember));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(ADMIN_ID, "admin@example.com", "Admin User"),
                user(TARGET_ID, "dev@example.com", "Dev User")
        ));

        List<MemberResponse> result = memberService.listMembers(WORKSPACE_ID, ADMIN_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MemberResponse::email)
                .containsExactlyInAnyOrder("admin@example.com", "dev@example.com");
    }

    @Test
    void listMembers_notMember_throwsWorkspaceNotFoundException() {
        when(memberRepository.existsByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> memberService.listMembers(WORKSPACE_ID, ADMIN_ID))
                .isInstanceOf(WorkspaceNotFoundException.class);
        verify(memberRepository, never()).findAllByWorkspaceId(any());
    }

    // ─── updateRole ───

    @Test
    void updateRole_success_returnsUpdatedMemberResponse() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(member(ADMIN_ID, WorkspaceRole.ADMIN)));
        WorkspaceMember target = member(TARGET_ID, WorkspaceRole.DEVELOPER);
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.of(target));
        when(memberRepository.save(any())).thenReturn(target);
        when(userRepository.findById(TARGET_ID))
                .thenReturn(Optional.of(user(TARGET_ID, "dev@example.com", "Dev User")));

        MemberResponse result = memberService.updateRole(WORKSPACE_ID, TARGET_ID, WorkspaceRole.PM, ADMIN_ID);

        assertThat(result.role()).isEqualTo("PM");
        assertThat(result.email()).isEqualTo("dev@example.com");
    }

    @Test
    void updateRole_notAdmin_throwsUnauthorizedAccessException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.of(member(TARGET_ID, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() ->
                memberService.updateRole(WORKSPACE_ID, ADMIN_ID, WorkspaceRole.PM, TARGET_ID))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void updateRole_targetNotMember_throwsWorkspaceNotFoundException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(member(ADMIN_ID, WorkspaceRole.ADMIN)));
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.updateRole(WORKSPACE_ID, TARGET_ID, WorkspaceRole.PM, ADMIN_ID))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    // ─── removeMember ───

    @Test
    void removeMember_success_deletesMemberRecord() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(member(ADMIN_ID, WorkspaceRole.ADMIN)));
        WorkspaceMember devTarget = member(TARGET_ID, WorkspaceRole.DEVELOPER);
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.of(devTarget));

        memberService.removeMember(WORKSPACE_ID, TARGET_ID, ADMIN_ID);

        verify(memberRepository).delete(devTarget);
    }

    @Test
    void removeMember_notAdmin_throwsUnauthorizedAccessException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.of(member(TARGET_ID, WorkspaceRole.DEVELOPER)));

        assertThatThrownBy(() ->
                memberService.removeMember(WORKSPACE_ID, ADMIN_ID, TARGET_ID))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(memberRepository, never()).delete(any(WorkspaceMember.class));
    }

    @Test
    void removeMember_lastAdmin_throwsLastAdminException() {
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, ADMIN_ID))
                .thenReturn(Optional.of(member(ADMIN_ID, WorkspaceRole.ADMIN)));
        WorkspaceMember adminTarget = member(ADMIN_ID, WorkspaceRole.ADMIN);
        when(memberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, TARGET_ID))
                .thenReturn(Optional.of(adminTarget));
        when(memberRepository.countByWorkspaceIdAndRole(WORKSPACE_ID, WorkspaceRole.ADMIN))
                .thenReturn(1L);

        assertThatThrownBy(() ->
                memberService.removeMember(WORKSPACE_ID, TARGET_ID, ADMIN_ID))
                .isInstanceOf(LastAdminException.class)
                .hasMessage("Cannot remove the only Admin");
        verify(memberRepository, never()).delete(any(WorkspaceMember.class));
    }
}
