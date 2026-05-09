package com.unityskill.workspace;

import com.unityskill.common.exception.AlreadyMemberException;
import com.unityskill.common.exception.InvitationNotFoundException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.mail.MailService;
import com.unityskill.workspace.dto.InvitationResponse;
import com.unityskill.workspace.dto.InviteLinkResponse;
import com.unityskill.workspace.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.invitation-expiry-days:7}")
    private int invitationExpiryDays;

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MailService mailService;

    @Transactional
    public InvitationResponse inviteByEmail(UUID workspaceId, String email, UUID inviterId) {
        requireAdmin(workspaceId, inviterId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);

        String token = generateToken();
        Instant expiresAt = Instant.now().plus(invitationExpiryDays, ChronoUnit.DAYS);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspaceId(workspaceId)
                .invitedBy(inviterId)
                .email(email)
                .token(token)
                .type(InvitationType.EMAIL)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        invitation = invitationRepository.saveAndFlush(invitation);

        String inviteUrl = frontendUrl + "/invite/" + token;
        mailService.sendInvitationEmail(email, workspace.getName(), inviteUrl, expiresAt);

        return InvitationResponse.from(invitation);
    }

    @Transactional
    public InviteLinkResponse generateInviteLink(UUID workspaceId, UUID inviterId) {
        requireAdmin(workspaceId, inviterId);
        workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);

        String token = generateToken();
        Instant expiresAt = Instant.now().plus(invitationExpiryDays, ChronoUnit.DAYS);

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspaceId(workspaceId)
                .invitedBy(inviterId)
                .token(token)
                .type(InvitationType.LINK)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        invitationRepository.saveAndFlush(invitation);

        String inviteUrl = frontendUrl + "/invite/" + token;
        return new InviteLinkResponse(token, inviteUrl, expiresAt.toString());
    }

    @Transactional
    public InvitationResponse acceptInvitation(String token, UUID userId) {
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(InvitationNotFoundException::new);

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationNotFoundException();
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new InvitationNotFoundException();
        }
        if (memberRepository.existsByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId)) {
            throw new AlreadyMemberException();
        }

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(invitation.getWorkspaceId())
                .userId(userId)
                .role(WorkspaceRole.DEVELOPER)
                .build();
        memberRepository.save(member);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation = invitationRepository.save(invitation);

        return InvitationResponse.from(invitation);
    }

    private void requireAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: not a workspace member"));
        if (member.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedAccessException("Access denied: Admin role required");
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
