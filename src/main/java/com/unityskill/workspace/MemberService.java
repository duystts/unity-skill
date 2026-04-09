package com.unityskill.workspace;

import com.unityskill.auth.UserRepository;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.LastAdminException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.common.exception.WorkspaceNotFoundException;
import com.unityskill.workspace.dto.MemberResponse;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    public List<MemberResponse> listMembers(UUID workspaceId, UUID callerId) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, callerId)) {
            throw new WorkspaceNotFoundException();
        }
        List<WorkspaceMember> members = memberRepository.findAllByWorkspaceId(workspaceId);
        List<UUID> userIds = members.stream().map(WorkspaceMember::getUserId).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return members.stream()
                .filter(m -> userMap.containsKey(m.getUserId()))
                .map(m -> MemberResponse.from(m, userMap.get(m.getUserId())))
                .toList();
    }

    @Transactional
    public MemberResponse updateRole(UUID workspaceId, UUID targetUserId, WorkspaceRole newRole, UUID callerId) {
        requireAdmin(workspaceId, callerId);
        WorkspaceMember target = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceNotFoundException::new);
        target.setRole(newRole);
        memberRepository.save(target);
        User user = userRepository.findById(targetUserId)
                .orElseThrow(WorkspaceNotFoundException::new);
        return MemberResponse.from(target, user);
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID targetUserId, UUID callerId) {
        requireAdmin(workspaceId, callerId);
        WorkspaceMember target = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(WorkspaceNotFoundException::new);
        if (target.getRole() == WorkspaceRole.ADMIN) {
            long adminCount = memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.ADMIN);
            if (adminCount <= 1) {
                throw new LastAdminException();
            }
        }
        memberRepository.delete(target);
    }

    private void requireAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: not a workspace member"));
        if (member.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedAccessException("Access denied: Admin role required");
        }
    }
}
