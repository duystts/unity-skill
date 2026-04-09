package com.unityskill.workspace;

import com.unityskill.workspace.entity.InvitationStatus;
import com.unityskill.workspace.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByToken(String token);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailAndStatus(
            UUID workspaceId, String email, InvitationStatus status);
}
